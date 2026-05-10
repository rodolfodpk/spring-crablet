    interface PublicationSink {
        void publish(StoredEvent event);
    }

    static final class ExamplePublisher implements OutboxPublisher {
        private final PublicationSink publicationSink;

        ExamplePublisher(PublicationSink publicationSink) {
            this.publicationSink = publicationSink;
        }

        @Override
        public void publishBatch(List<StoredEvent> events) {
            for (StoredEvent event : events) {
                publicationSink.publish(event);
            }
        }

        @Override
        public String getName() {
            return "ExamplePublisher";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
