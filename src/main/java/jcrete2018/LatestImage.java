package jcrete2018;

import java.net.URLEncoder;
import java.util.Arrays;
import javax.ejb.Startup;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.tweetwallfx.tweet.api.TweetQuery;
import org.tweetwallfx.tweet.api.Tweeter;
import org.tweetwallfx.tweet.api.entry.MediaTweetEntryType;
import org.tweetwallfx.tweet.impl.twitter4j.TwitterTweeter;

@Startup
@Singleton
@Path("")
public class LatestImage {

    private final Tweeter tweeter = new TwitterTweeter();

    @Path("latest")
    @GET
    @Produces(value = MediaType.TEXT_HTML)
    public String page() {
        final String mediaUrl = tweeter.search(
                new TweetQuery()
                        .resultType(TweetQuery.ResultType.recent)
                        .query("JCreteCharity")
                        //                        .query("bitsofpluto")
                        .count(10))
                .flatMap(t -> Arrays.stream(t.getMediaEntries()))
                .filter(me -> me.getType() == MediaTweetEntryType.photo)
                .map(me -> me.getMediaUrl())
                .findFirst()
                .orElse("");

        return "<html>"
                + "<meta http-equiv=\"refresh\" content=\"10\"/>"
                + "<head>"
                + "<style>"
                + "body { \n"
                + "  background: url(" + mediaUrl + ") no-repeat center center fixed; \n"
                + "  -webkit-background-size: cover;\n"
                + "  -moz-background-size: cover;\n"
                + "  -o-background-size: cover;\n"
                + "  background-size: cover;\n"
                + "}"
                + "</style>"
                + "</head>"
                + "<body></body>"
                + "</html>";
    }
}
