package ru.yandex.practicum.analyzer.deserializer;

import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;

import java.io.IOException;

public class HubEventDeserializer implements Deserializer<HubEventAvro> {
    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private final DatumReader<HubEventAvro> reader = new SpecificDatumReader<>(HubEventAvro.getClassSchema());

    @Override
    public HubEventAvro deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            Decoder decoder = decoderFactory.binaryDecoder(data, null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new SerializationException("Ошибка десериализации HubEventAvro", e);
        }
    }
}