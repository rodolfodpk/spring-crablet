    interface KafkaTemplate<K, V> {
        void send(String topic, V value);
    }

    static final class KafkaPublisher implements OutboxPublisher {
        private final KafkaTemplate<String, StoredEvent> kafkaTemplate;

        KafkaPublisher(KafkaTemplate<String, StoredEvent> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public void publishBatch(List<StoredEvent> events) {
            for (StoredEvent event : events) {
                kafkaTemplate.send("default", event);
            }
        }

        @Override
        public String getName() {
            return "KafkaPublisher";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
