package crypto

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"io"
	"strings"
)

const (
	aesGCMNonceSize = 12
	aesGCMTagSize   = 16
)

type EncryptionService struct {
	enabled bool
	key     []byte
}

func NewEncryptionService(enabled bool, keyMaterial string) (*EncryptionService, error) {
	svc := &EncryptionService{enabled: enabled}
	if !enabled {
		return svc, nil
	}
	if keyMaterial == "" {
		key := make([]byte, 32)
		if _, err := io.ReadFull(rand.Reader, key); err != nil {
			return nil, err
		}
		svc.key = key
		return svc, nil
	}
	key, err := decodeKey(keyMaterial)
	if err != nil {
		return nil, err
	}
	svc.key = key
	return svc, nil
}

func decodeKey(encoded string) ([]byte, error) {
	if decoded, err := base64.StdEncoding.DecodeString(encoded); err == nil {
		return decoded, nil
	}
	if decoded, err := hex.DecodeString(encoded); err == nil {
		return decoded, nil
	}
	return nil, errors.New("encryption key must be base64 or hex")
}

func (s *EncryptionService) IsEnabled() bool {
	return s.enabled && len(s.key) == 32
}

func (s *EncryptionService) Encrypt(plaintext string) (string, error) {
	if !s.enabled || plaintext == "" {
		return plaintext, nil
	}
	block, err := aes.NewCipher(s.key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, aesGCMNonceSize)
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	ciphertext := gcm.Seal(nil, nonce, []byte(plaintext), nil)
	buf := bytes.NewBuffer(make([]byte, 0, len(nonce)+len(ciphertext)))
	buf.Write(nonce)
	buf.Write(ciphertext)
	return base64.StdEncoding.EncodeToString(buf.Bytes()), nil
}

func (s *EncryptionService) Decrypt(ciphertext string) (string, error) {
	if !s.enabled || ciphertext == "" {
		return ciphertext, nil
	}
	decoded, err := base64.StdEncoding.DecodeString(ciphertext)
	if err != nil {
		return "", err
	}
	if len(decoded) < aesGCMNonceSize+aesGCMTagSize {
		return "", errors.New("ciphertext too short")
	}
	block, err := aes.NewCipher(s.key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := decoded[:aesGCMNonceSize]
	plaintext, err := gcm.Open(nil, nonce, decoded[aesGCMNonceSize:], nil)
	if err != nil {
		return "", err
	}
	return string(plaintext), nil
}

type PciDataMasker struct{}

func (PciDataMasker) MaskCardNumber(cardNumber string) string {
	if len(cardNumber) < 4 {
		return "****"
	}
	return strings.Repeat("*", len(cardNumber)-4) + cardNumber[len(cardNumber)-4:]
}

func (PciDataMasker) MaskCvv(string) string { return "***" }

func (PciDataMasker) MaskEmail(email string) string {
	if !strings.Contains(email, "@") {
		return "***@***"
	}
	at := strings.Index(email, "@")
	if at <= 1 {
		return "*" + email[at:]
	}
	return string(email[0]) + "***" + email[at:]
}

func (PciDataMasker) MaskToken(token string) string {
	if len(token) < 8 {
		return "****"
	}
	return token[:4] + "..." + token[len(token)-4:]
}

func (PciDataMasker) MaskAddress(address string) string {
	if len(address) < 5 {
		return "***"
	}
	return address[:3] + "***"
}
