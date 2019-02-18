package org.kafkahq.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.sse.Event;
import io.micronaut.views.View;
import io.micronaut.views.freemarker.FreemarkerViewsRenderer;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.disposables.Disposable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.httpcache4j.uri.URIBuilder;
import org.kafkahq.models.Config;
import org.kafkahq.models.Record;
import org.kafkahq.models.Topic;
import org.kafkahq.modules.RequestHelper;
import org.kafkahq.repositories.ConfigRepository;
import org.kafkahq.repositories.RecordRepository;
import org.kafkahq.repositories.TopicRepository;
import org.kafkahq.utils.Debug;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.Buffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Controller("${micronaut.context.path:}/{cluster}/topic")
public class TopicController extends AbstractController {
    private TopicRepository topicRepository;
    private ConfigRepository configRepository;
    private RecordRepository recordRepository;
    private FreemarkerViewsRenderer freemarkerViewsRenderer;

    @Inject
    public TopicController(TopicRepository topicRepository, ConfigRepository configRepository, RecordRepository recordRepository, FreemarkerViewsRenderer freemarkerViewsRenderer) {
        this.topicRepository = topicRepository;
        this.configRepository = configRepository;
        this.recordRepository = recordRepository;
        this.freemarkerViewsRenderer = freemarkerViewsRenderer;
    }

    @View("topicList")
    @Get
    public HttpResponse list(HttpRequest request, String cluster, Optional<String> search) throws ExecutionException, InterruptedException {
        return this.template(
            request,
            cluster,
            "search", search,
            "topics", this.topicRepository.list(search)
        );
    }

    @View("topicCreate")
    @Get("create")
    public HttpResponse create(HttpRequest request, String cluster) {
        return this.template(
            request,
            cluster
        );
    }

    @Post(value = "create", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse createSubmit(HttpRequest request,
                                     String cluster,
                                     String name,
                                     Integer partition,
                                     Short replication,
                                     Map<String, String> configs)
        throws Throwable
    {
        List<Config> options = configs
            .entrySet()
            .stream()
            .map(r -> new AbstractMap.SimpleEntry<>(
                r.getKey().replaceAll("(configs\\[)(.*)(])", "$2"),
                r.getValue()
            ))
            .map(r -> new Config(r.getKey(), r.getValue()))
            .collect(Collectors.toList());

        MutableHttpResponse<Void> response = HttpResponse.redirect(new URI(("/" + cluster + "/topic")));

        this.toast(response, RequestHelper.runnableToToast(() ->
                this.topicRepository.create(
                    cluster,
                    name,
                    partition,
                    replication,
                    options
                ),
            "Topic '" + name + "' is created",
            "Failed to create topic '" + name + "'"
        ));

        return response;
    }

    @View("topicProduce")
    @Get("{topicName}/produce")
    public HttpResponse produce(HttpRequest request, String cluster, String topicName) throws ExecutionException, InterruptedException {
        Topic topic = this.topicRepository.findByName(topicName);

        return this.template(
            request,
            cluster,
            "topic", topic
        );
    }

    @Post(value = "{topicName}/produce", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse produceSubmit(HttpRequest request,
                                      String cluster,
                                      String topicName,
                                      String value,
                                      Optional<String> key,
                                      Optional<Integer> partition,
                                      Optional<String> timestamp,
                                      Map<String, List<String>> headers)
    {
        Map<String, String> finalHeaders = new HashMap<>();

        int i = 0;
        for (String headerKey : headers.get("headers[key]")) {
            if (headerKey != null && !headerKey.equals("") && headers.get("headers[value]").get(i) != null && !headers.get("headers[value]").get(i).equals("")) {
                finalHeaders.put(headerKey, headers.get("headers[value]").get(i));
            }
            i++;
        }

        MutableHttpResponse<Void> response = HttpResponse.redirect(request.getUri());

        this.toast(response, RequestHelper.runnableToToast(() ->
                this.recordRepository.produce(
                    cluster,
                    topicName,
                    value,
                    finalHeaders,
                    key.filter(r -> !r.equals("")),
                    partition,
                    timestamp.filter(r -> !r.equals("")).map(r -> Instant.parse(r).toEpochMilli())
                )
            ,
            "Record created",
            "Failed to produce record"
        ));

        return response;
    }

    @View("topic")
    @Get("{topicName}")
    public HttpResponse home(HttpRequest request,
                             String cluster,
                             String topicName,
                             Optional<String> after,
                             Optional<Integer> partition,
                             Optional<RecordRepository.Options.Sort> sort,
                             Optional<String> timestamp,
                             Optional<String> search)
        throws ExecutionException, InterruptedException
    {
        Topic topic = this.topicRepository.findByName(topicName);

        RecordRepository.Options options = new RecordRepository.Options(cluster, topicName);
        after.ifPresent(options::setAfter);
        partition.ifPresent(options::setPartition);
        sort.ifPresent(options::setSort);
        timestamp.map(r -> Instant.parse(r).toEpochMilli()).ifPresent(options::setTimestamp);
        after.ifPresent(options::setAfter);
        search.ifPresent(options::setSearch);

        List<Record> data = new ArrayList<>();

        if (options.getSearch() == null) {
            data = this.recordRepository.consume(options);
        }

        URIBuilder uri = URIBuilder.fromURI(request.getUri());

        ImmutableMap.Builder<String, String> partitionUrls = ImmutableSortedMap.naturalOrder();
        partitionUrls.put((uri.getParametersByName("partition").size() > 0 ? uri.removeParameters("partition") : uri).toNormalizedURI(false).toString(), "All");
        for (int i = 0; i < topic.getPartitions().size(); i++) {
            partitionUrls.put(uri.addParameter("partition", String.valueOf(i)).toNormalizedURI(false).toString(), String.valueOf(i));
        }

        return this.template(
            request,
            cluster,
            "tab", "data",
            "topic", topic,
            "canDeleteRecords", topic.canDeleteRecords(configRepository),
            "datas", data,
            "navbar", dataNavbar(options, uri, partitionUrls),
            "pagination", dataPagination(topic, options, data, uri)
        );
    }

    private ImmutableMap<Object, Object> dataPagination(Topic topic, RecordRepository.Options options, List<Record> data, URIBuilder uri) {
        return ImmutableMap.builder()
            .put("size", options.getPartition() == null ? topic.getSize() : topic.getSize(options.getPartition()))
            .put("before", options.before(data, uri).toNormalizedURI(false).toString())
            .put("after", options.after(data, uri).toNormalizedURI(false).toString())
            .build();
    }

    private ImmutableMap<Object, Object> dataNavbar(RecordRepository.Options options, URIBuilder uri, ImmutableMap.Builder<String, String> partitionUrls) {
        return ImmutableMap.builder()
            .put("partition", ImmutableMap.builder()
                .put("current", Optional.ofNullable(options.getPartition()))
                .put("values", partitionUrls.build())
                .build()
            )
            .put("sort", ImmutableMap.builder()
                .put("current", Optional.ofNullable(options.getSort()))
                .put("values", ImmutableMap.builder()
                    .put(uri.addParameter("sort", RecordRepository.Options.Sort.NEWEST.name()).toNormalizedURI(false).toString(), RecordRepository.Options.Sort.NEWEST.name())
                    .put(uri.addParameter("sort", RecordRepository.Options.Sort.OLDEST.name()).toNormalizedURI(false).toString(), RecordRepository.Options.Sort.OLDEST.name())
                    .build()
                )
                .build()
            )
            .put("timestamp", ImmutableMap.builder()
                .put("current", Optional.ofNullable(options.getTimestamp()))
                .build()
            )
            .put("search", ImmutableMap.builder()
                .put("current", Optional.ofNullable(options.getSearch()))
                .build()
            )
            .build();
    }

    @View("topic")
    @Get("{topicName}/{tab:(partitions|groups|configs|logs)}")
    public HttpResponse tab(HttpRequest request, String cluster, String topicName, String tab) throws ExecutionException, InterruptedException {
        return this.render(request, cluster, topicName,  tab);
    }

    private HttpResponse render(HttpRequest request, String cluster, String topicName, String tab) throws ExecutionException, InterruptedException {
        Topic topic = this.topicRepository.findByName(topicName);
        List<Config> configs = this.configRepository.findByTopic(topicName);

        return this.template(
            request,
            cluster,
            "tab", tab,
            "topic", topic,
            "configs", configs
        );
    }

    @Post(value = "{topicName}/configs", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse updateConfig(HttpRequest request, String cluster, String topicName, Map<String, String> configs) throws Throwable {
        List<Config> updated = ConfigRepository.updatedConfigs(configs, this.configRepository.findByTopic(topicName));
        MutableHttpResponse<Void> response = HttpResponse.redirect(request.getUri());

        this.toast(response, RequestHelper.runnableToToast(() -> {
                if (updated.size() == 0) {
                    throw new IllegalArgumentException("No config to update");
                }

                this.configRepository.updateTopic(
                    cluster,
                    topicName,
                    updated
                );
            },
            "Topic configs '" + topicName + "' is updated",
            "Failed to update topic '" + topicName + "' configs"
        ));

        return response;
    }

    @Get("{topicName}/deleteRecord")
    public HttpResponse deleteRecord(String cluster, String topicName, Integer partition, String key) {
        MutableHttpResponse<Void> response = HttpResponse.ok();

        this.toast(response, RequestHelper.runnableToToast(() -> this.recordRepository.delete(
                cluster,
                topicName,
                partition,
                Base64.getDecoder().decode(key)
            ),
            "Record '" + key + "' will be deleted on compaction",
            "Failed to delete record '" + key + "'"
        ));

        return response;
    }

    @Get("{topicName}/delete")
    public HttpResponse delete(String cluster, String topicName) {
        MutableHttpResponse<Void> response = HttpResponse.ok();

        this.toast(response, RequestHelper.runnableToToast(() ->
                this.topicRepository.delete(cluster, topicName),
            "Topic '" + topicName + "' is deleted",
            "Failed to delete topic " + topicName
        ));

        return response;
    }


    @Get("test")
    public Publisher<Event<RecordRepository.SearchEnd>> index() {
        String[] versions = new String[]{"1.0", "2.0"};

        return Flowable.generate(() -> 0, (i, emitter) -> {
            if (i < versions.length) {
                emitter.onNext(
                    Event.of(new RecordRepository.SearchEnd("Micronaut " + versions[i] + " Released"))
                );
                Debug.print(versions[i]);
                Thread.sleep(2000);
            } else {
                emitter.onComplete();
            }
            return ++i;
        });
    }

    @Get("{topicName}/search/{search}")
    public Publisher<Event<?>> sse(String cluster,
                                          String topicName,
                                          Optional<String> after,
                                          Optional<Integer> partition,
                                          Optional<RecordRepository.Options.Sort> sort,
                                          Optional<String> timestamp,
                                          Optional<String> search)
        throws ExecutionException, InterruptedException
    {
        Topic topic = topicRepository.findByName(topicName);

        RecordRepository.Options options = new RecordRepository.Options(cluster, topicName);
        after.ifPresent(options::setAfter);
        partition.ifPresent(options::setPartition);
        sort.ifPresent(options::setSort);
        timestamp.map(r -> Instant.parse(r).toEpochMilli()).ifPresent(options::setTimestamp);
        after.ifPresent(options::setAfter);
        search.ifPresent(options::setSearch);

        Map<String, Object> datas = new HashMap<>();
        datas.put("topic", topic);
        datas.put("canDeleteRecords", topic.canDeleteRecords(configRepository));
        datas.put("clusterId", cluster);
        datas.put("basePath", getBasePath());

        FlowableOnSubscribe<Event<?>> flowableOnSubscribe = emitter -> {

            emitter.onNext(Event
                .of(new RecordRepository.SearchEnd("1"))
                .name("searchEnd")
            );

            Debug.print("1");
            Thread.sleep(1000);

            emitter.onNext(Event
                .of(new RecordRepository.SearchEnd("2"))
                .name("searchEnd")
            );
            Debug.print("2");
            Thread.sleep(1000);
            emitter.onComplete();
        };

        return Flowable.create(flowableOnSubscribe, BackpressureStrategy.ERROR);

        /*
        return Flowable.unsafeCreate((emitter) -> {
            RecordRepository.SearchConsumer searchConsumer = new RecordRepository.SearchConsumer() {
                @Override
                public void accept(RecordRepository.SearchEvent searchEvent) {
                    datas.put("datas", searchEvent.getRecords());

                    StringWriter stringWriter = new StringWriter();
                    try {
                        freemarkerViewsRenderer.render("topicSearch", datas).writeTo(stringWriter);
                    } catch (IOException ignored) {}

                    emitter.onNext(Event
                        .of(new DataSseController.SearchBody(
                            searchEvent.getOffsets(),
                            searchEvent.getProgress(),
                            stringWriter.toString()
                        ))
                        .name("searchBody")
                    );
                }
            };


//             emitter.setCancellable(searchConsumer::close);
            RecordRepository.SearchEnd end = null;
            try {
                end = recordRepository.search(options, searchConsumer);
            } catch (ExecutionException | InterruptedException e) {
                emitter.onError(e);
            }

            emitter.onNext(Event
                .of(end)
                .name("searchEnd")
            );

            emitter.onComplete();
        });
        */
    }

    @ToString
    @EqualsAndHashCode
    @Getter
    @AllArgsConstructor
    public static class SearchBody {
        @JsonProperty("offsets")
        private Map<Integer, RecordRepository.SearchEvent.Offset> offsets = new HashMap<>();

        @JsonProperty("progress")
        private Map<Integer, Long> progress = new HashMap<>();

        @JsonProperty("body")
        private String body;
    }
}
