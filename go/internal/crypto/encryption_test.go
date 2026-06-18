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

	pan := "4242424242424242"
	cvv := "123"
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
	if got := masker.MaskCardNumber(pan); got != "************4242" {
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
}
