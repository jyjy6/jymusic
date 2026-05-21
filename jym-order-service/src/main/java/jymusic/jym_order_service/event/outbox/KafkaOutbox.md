OrderService.createOrder()  ─ @Transactional 시작
│
├─ orderRepository.save(order)                       → 같은 트랜잭션
├─ cartRepository.save(cart)                         → 같은 트랜잭션
└─ recordOrderCreatedToOutbox(savedOrder)
        │
        └─ OutboxEventRecorder.record(...)           → @Transactional(MANDATORY)
                ├─ outboxEventRepository.save(event) → 같은 트랜잭션
                └─ applicationEventPublisher.publishEvent(new OutboxEventRecorded())
                            │
                            │  ※ Spring 이 큐에 잠시 보관 (트랜잭션 commit 기다림)
                            ▼
                       [메모리 큐에 대기]
│
[메서드 종료 → @Transactional 끝 → DB COMMIT]
│
▼
[Spring 이 큐에서 OutboxEventRecorded 를 꺼냄]
│
└─ phase 가 AFTER_COMMIT 인 리스너만 호출
        │
        └─ OutboxPublisher.onOutboxEventRecorded(...)
                │
                └─ publishPending()
                        ├─ SELECT ... WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED
                        └─ kafkaTemplate.send(...)  ← 실제 Kafka 발행
                        
핵심: AFTER_COMMIT 은 commit 이 성공한 직후에만 발동 합니다. 만약 트랜잭션이 롤백되면? Spring 이 큐에서 그냥 이벤트를 폐기합니다. → outbox row 도 어차피 같이 롤백돼서 없어졌으니 발행할 게 없죠. 정합성 OK.