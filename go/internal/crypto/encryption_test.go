package crypto

import (
	"encoding/base64"
	"strings"
	"testing"
)

func TestEncryptionRoundTripAndMasking(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i + 1)
	}
	svc, err := NewEncryptionService(true, base64.StdEncoding.EncodeToString(key))
	if err != nil {
		t.Fatalf("new encryption service: %v", err)
	}

	pan := "tok_test_4242"
	cvv := "test-cvc"
	ciphertext, err := svc.Encrypt(pan)
	if err != nil {
		t.Fatalf("encrypt: %v", err)
	}
	if ciphertext == "" {
		t.Fatalf("ciphertext is empty")
	}
	if strings.Contains(ciphertext, pan) || strings.Contains(ciphertext, cvv) {
		t.Fatalf("ciphertext leaked sensitive data: %s", ciphertext)
	}

	plaintext, err := svc.Decrypt(ciphertext)
	if err != nil {
		t.Fatalf("decrypt: %v", err)
	}
	if plaintext != pan {
		t.Fatalf("decrypt = %q, want %q", plaintext, pan)
	}

	masker := PciDataMasker{}
	if got := masker.MaskCardNumber(pan); got != "*********4242" {
		t.Fatalf("masked card = %q", got)
	}
	if got := masker.MaskCvv(cvv); got != "***" {
		t.Fatalf("masked cvv = %q", got)
	}
	if got := masker.MaskEmail("user@example.com"); got != "u***@example.com" {
		t.Fatalf("masked email = %q", got)
	}
	if got := masker.MaskAddress("123 Main St"); got != "123***" {
		t.Fatalf("masked address = %q", got)
	}

	t.Run("rejects missing or invalid key material when enabled", func(t *testing.T) {
		if _, err := NewEncryptionService(true, ""); err == nil {
			t.Fatalf("expected empty key error")
		}
		shortKey := base64.StdEncoding.EncodeToString(make([]byte, 16))
		if _, err := NewEncryptionService(true, shortKey); err == nil {
			t.Fatalf("expected short key error")
		}
	})

	t.Run("disabled or unkeyed service bypasses encryption", func(t *testing.T) {
		svc := &EncryptionService{enabled: true}
		if got, err := svc.Encrypt("plain"); err != nil || got != "plain" {
			t.Fatalf("encrypt = %q, %v; want plain,nil", got, err)
		}
		if got, err := svc.Decrypt("cipher"); err != nil || got != "cipher" {
			t.Fatalf("decrypt = %q, %v; want cipher,nil", got, err)
		}
	})
}
