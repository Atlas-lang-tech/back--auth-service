#!/bin/bash

# Генерация RSA ключей для JWT подписи
# Запускать из корня проекта: ./generate-keys.sh

KEYS_DIR="src/main/resources/META-INF/resources"
mkdir -p "$KEYS_DIR"

echo "Генерация RSA ключей..."

# Приватный ключ
openssl genrsa -out "$KEYS_DIR/privateKey.pem" 2048

# Публичный ключ из приватного
openssl rsa -in "$KEYS_DIR/privateKey.pem" -pubout -out "$KEYS_DIR/publicKey.pem"

echo "Ключи созданы:"
echo "  Приватный: $KEYS_DIR/privateKey.pem"
echo "  Публичный: $KEYS_DIR/publicKey.pem"
echo ""
echo "ВАЖНО: Добавь privateKey.pem в .gitignore!"
echo ""
echo "Добавить в .gitignore:"
echo "  src/main/resources/META-INF/resources/privateKey.pem"
