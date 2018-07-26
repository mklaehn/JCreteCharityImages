package jcrete2018;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("")
public class LatestImage {

    @Path("hello")
    @GET
    @Produces(value = MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello txt!";
    }
}
