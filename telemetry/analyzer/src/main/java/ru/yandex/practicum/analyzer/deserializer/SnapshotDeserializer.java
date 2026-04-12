package ru.yandex.practicum.analyzer.deserializer;

import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.io.IOException;

public class SnapshotDeserializer implements Deserializer<SensorsSnapshotAvro> {
    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private final DatumReader<SensorsSnapshotAvro> reader =
            new SpecificDatumReader<>(SensorsSnapshotAvro.getClassSchema());

    @Override
    public SensorsSnapshotAvro deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            Decoder decoder = decoderFactory.binaryDecoder(data, null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new SerializationException("Ошибка десериализации SensorsSnapshotAvro", e);
        }
    }
}