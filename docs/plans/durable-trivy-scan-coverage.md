# Plan: Durable Trivy scan coverage for shopping-cart-payment

**Status:** spec — for Codex implementation (spec + Codex discipline; do not hand-edit live)
**Owner repo:** shopping-cart-payment
**Driver:** k3d-manager CVE panel ② ("Shopping-cart Unique CVEs")
**Cross-ref:** k3d-manager `docs/bugs/2026-08-24-trivy-operator-skips-private-images-sa-imagepullsecret.md`

---

## Context

trivy-operator on the `ubuntu-hostinger` app-cluster now scans the private
`ghcr.io/wilddog64/shopping-cart-*` images natively via the operator-side
`operator.privateRegistryScanSecretsNames` map (set in k3d-manager
`scripts/etc/helm/observability/trivy-operator-acg-values.yaml`, commit `aac9cb27`).
Because that map names a per-namespace pull secret, **the operator runs its scan Job _inside_
the target namespace** (it must mount `ghcr-pull-secret` there).

`shopping-cart-payment` is PCI-scoped and ships a `default-deny-all` NetworkPolicy
(`k8s/base/networkpolicy.yaml`). That deny blocks the in-namespace scan Job's egress, so the
payment image is **not** covered unless an egress allow exists for the scan pod. This was applied
**live** as `allow-cve-scan-egress` (podSelector `app.kubernetes.io/managed-by=trivy-operator`,
egress `[{}]`) to prove coverage, but that is **drift** — it lives only on the cluster and will be
lost on the next reconcile/rebuild. This plan gives it a durable home in git.

(For contrast, the four `shopping-cart-apps` workloads have no default-deny, so they scan without a
companion policy. Only payment needs this.)

## Goal 1 (REQUIRED) — durable scan-egress NetworkPolicy

Add a NetworkPolicy that lets the trivy-operator scan pod egress from the payment namespace, kept
as tight as PCI scope allows.

### New file: `k8s/base/networkpolicy-cve-scan.yaml`

```yaml
---
# Allow egress for the trivy-operator scan Job that runs in this namespace
# (operator.privateRegistryScanSecretsNames mounts ghcr-pull-secret here, so the
# scan Job is created in-namespace and must reach DNS, the registry, and trivy-server).
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-cve-scan-egress
  namespace: shopping-cart-payment
  labels:
    pci-scope: "true"
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/managed-by: trivy-operator
  policyTypes:
  - Egress
  egress:
  # DNS
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
  # trivy-server (ClientServer mode)
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: trivy-system
    ports:
    - protocol: TCP
      port: 4954
  # registry (ghcr.io layer pulls) over HTTPS
  - ports:
    - protocol: TCP
      port: 443
```

### ⚠️ kustomize gotcha — `commonLabels` mangles the selector

`k8s/base/kustomization.yaml` uses **`commonLabels:`** (`app.kubernetes.io/part-of: shopping-cart`,
`version`). `commonLabels` applies labels to **selectors too**, so it would inject
`part-of=shopping-cart` into this policy's `spec.podSelector.matchLabels` — and the trivy scan pod
does **not** carry that label, so the policy would select nothing and silently fail to allow the
scan. The existing per-service policies are fine because their target (`payment-service`) does carry
the common labels; the scan pod does not.

Implement **one** of:
- **(preferred)** migrate the `commonLabels:` block to the newer `labels:` transformer with
  `includeSelectors: false` (the file already uses that form for the `managed-by: kustomize` pair —
  fold `part-of`/`version` into it), so no common label reaches any selector; **or**
- keep `commonLabels` but exclude this file's selector via a `patches:`/`transformers:` step that
  strips `part-of`/`version` from `allow-cve-scan-egress`'s `podSelector`.

Add `- networkpolicy-cve-scan.yaml` to `kustomization.yaml` `resources:`.

### Egress-scope fallback

The tightened egress above (DNS + trivy-server:4954 + 443) is the target. If live verification shows
the scan Job still cannot complete (e.g. the payment namespace is Istio-sidecar-injected and the scan
pod needs istiod:15012 or broader mesh egress), widen incrementally — add a `trivy-system`/istio
egress rule — rather than reverting to allow-all `egress: [{}]`. Record whatever is required.

## Goal 2 (OPTIONAL hardening) — explicit pull secret on the ServiceAccount

Scanning already works via the operator-side named secret, so this is **defense-in-depth only**, not
required. If adopted, it makes the pull credential explicit on the workload instead of relying on the
node-level containerd credential.

In `k8s/base/serviceaccount.yaml`, add to the `payment-service` ServiceAccount:

```yaml
imagePullSecrets:
- name: ghcr-pull-secret
```

Preconditions: `ghcr-pull-secret` must exist durably in `shopping-cart-payment` (confirm it is
ESO-managed or otherwise reconciled, not a one-off). Keep `automountServiceAccountToken: false`.
If this is done, the operator would discover the pull secret even without
`privateRegistryScanSecretsNames`, but keep the operator-side map as-is (it also covers the
`shopping-cart-apps` workloads and is harmless overlap). Do **not** roll this to the other four
`shopping-cart-*` repos as part of this plan — track separately if wanted.

## Acceptance

1. `kubectl kustomize k8s/base | kubectl apply --dry-run=client -f -` succeeds.
2. Rendered `allow-cve-scan-egress` `podSelector` is **exactly** `{app.kubernetes.io/managed-by:
   trivy-operator}` — no `part-of`/`version` leaked in (the gotcha above).
3. Live on `ubuntu-hostinger`: restart the operator (or wait for the 24h report TTL), confirm
   `kubectl -n shopping-cart-payment get vulnerabilityreports` shows a fresh
   `...payment-service` report (scan Job completed, not blocked).
4. k3d-manager hub Prometheus: `count(trivy_vulnerability_inventory{image_repository=~"wilddog64/
   shopping-cart-payment.*"}) > 0` stays true across an operator restart (no longer drift-dependent).
5. `default-deny-all` and every existing `payment-service` policy are unchanged.

## Out of scope

- The operator-side `privateRegistryScanSecretsNames` config (owned by k3d-manager, already shipped).
- The four `shopping-cart-apps` workloads (scan without a companion policy — no default-deny there).
