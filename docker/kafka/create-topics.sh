#!/bin/bash
# Kafka Topic 생성 스크립트
# 사용법: docker exec -it jym-kafka bash /scripts/create-topics.sh

KAFKA_BIN=/usr/bin
BOOTSTRAP=localhost:29092

echo "=== Creating Kafka Topics ==="

# 메인 토픽
$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.order.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.payment.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.stock.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

# Dead Letter Topics
$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.order.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.payment.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.stock.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

echo "=== Topic List ==="
$KAFKA_BIN/kafka-topics --list --bootstrap-server $BOOTSTRAP

echo "=== Done ==="
