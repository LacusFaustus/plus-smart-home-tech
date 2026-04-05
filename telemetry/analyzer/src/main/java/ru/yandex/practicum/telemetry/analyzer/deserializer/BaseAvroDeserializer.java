package ru.yandex.practicum.telemetry.analyzer.deserializer;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class BaseAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseAvroDeserializer.class);
    protected final DatumReader<T> reader;

    public BaseAvroDeserializer(Schema schema) {
        this.reader = new SpecificDatumReader<>(schema);
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            log.debug("Received null or empty data from topic: {}", topic);
            return null;
        }

        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            T result = reader.read(null, decoder);
            log.trace("Successfully deserialized message from topic: {}", topic);
            return result;
        } catch (IOException e) {
            log.error("Failed to deserialize Avro message from topic: {}", topic, e);
            throw new RuntimeException("Failed to deserialize Avro message", e);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }
}