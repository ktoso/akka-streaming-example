package akka.http.javadsl;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.example.RichWikipediaEntry;
import akka.http.example.WikipediaEntry;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorMaterializer;
import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.apache.kafka.clients.producer.ProducerRecord;
import scala.NotImplementedError;

import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

public class JavaETLExample {

  /* Here we'll show off the Streaming XML capabilities */
  static Flow<ByteString, WikipediaEntry, NotUsed> parseWikiEntries() {
    return NOT_IMPLEMENTED();
  }

  /* Here we're showing off parallel fetching of additional data using Akka HTTP, the response is an image */
  static Flow<WikipediaEntry, RichWikipediaEntry, NotUsed> enrichWithImageData(ActorSystem system, Materializer mat) {
    final int parallelism = Runtime.getRuntime().availableProcessors();
    final Http http = Http.get(system);

    return Flow.of(WikipediaEntry.class)
      .mapAsyncUnordered(parallelism, w -> {
        final HttpRequest request = HttpRequest.create("http://images.example.com/query?" + w.title());

        return http.singleRequest(request, mat)
          .thenCompose(response -> {
              final CompletionStage<HttpEntity.Strict> strictCompletionStage = response.entity().toStrict(1000, mat);
              return strictCompletionStage.thenApply(strict -> new RichWikipediaEntry(w, strict.getData()));
            }
          );
      });
  }

  /* This stores wikipedia contents to Kafka */
  static Sink<RichWikipediaEntry, NotUsed> wikipediaKafkaTopic(ProducerSettings producerSettings) {
    return Flow.of(RichWikipediaEntry.class)
      .map(i -> i.wikipediaEntry().content())
      .map(elem -> new ProducerRecord("contents", elem))
      .to(Producer.plainSink(producerSettings));
  }

  /* This is an imaginary S3 Sink */
  public static Sink<RichWikipediaEntry, NotUsed> s3ImageStorage() {
    return NOT_IMPLEMENTED();
  }

  public static void main() {
    // kafka producer settings
    final ActorSystem system = ActorSystem.create();
    final Materializer mat = ActorMaterializer.create(system);

    ProducerSettings producerSettings = NOT_IMPLEMENTED();

  /* Combining the pipeline: */
    Source<WikipediaEntry, CompletionStage<IOResult>> wikipediaEntries =
      FileIO.fromPath(Paths.get("/tmp", "wiki"))
        .via(parseWikiEntries());


    Source<RichWikipediaEntry, CompletionStage<IOResult>> enrichedData =
      wikipediaEntries
        .via(enrichWithImageData(system, mat));

    enrichedData
      .alsoTo(s3ImageStorage())
      .to(wikipediaKafkaTopic(producerSettings))
      .run(mat);
  }

  static <T> T NOT_IMPLEMENTED() {
    throw new NotImplementedError("");
  }


}
