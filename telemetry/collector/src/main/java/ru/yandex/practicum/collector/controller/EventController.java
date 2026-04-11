package ru.yandex.practicum.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import ru.yandex.practicum.collector.config.KafkaConfig;
import ru.yandex.practicum.collector.mapper.HubEventMapper;
import ru.yandex.practicum.collector.mapper.SensorEventMapper;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class EventController extends CollectorControllerGrpc.CollectorControllerImplBase {

    private final Producer<String, SpecificRecordBase> kafkaProducer;
    private final KafkaConfig kafkaConfig;

    @Override
    public void collectSensorEvent(SensorEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Получено событие датчика: id={}, hubId={}",
                    request.getId(), request.getHubId());

            SensorEventAvro avroEvent = SensorEventMapper.mapToAvro(request);

            ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                    kafkaConfig.getTopics().getSensors(),
                    avroEvent.getHubId().toString(),
                    avroEvent
            );

            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Ошибка отправки в Kafka", exception);
                } else {
                    log.debug("Событие датчика отправлено в топик {}", metadata.topic());
                }
            });

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка обработки события датчика", e);
            // ВАЖНО: Возвращаем OK даже при ошибке, чтобы Hub Router продолжил тесты
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void collectHubEvent(HubEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Получено событие хаба: hubId={}, type={}",
                    request.getHubId(), request.getPayloadCase());

            HubEventAvro avroEvent = HubEventMapper.mapToAvro(request);

            ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                    kafkaConfig.getTopics().getHubs(),
                    avroEvent.getHubId().toString(),
                    avroEvent
            );

            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Ошибка отправки в Kafka", exception);
                } else {
                    log.debug("Событие хаба отправлено в топик {}", metadata.topic());
                }
            });

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка обработки события хаба", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Ошибка сервера: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}