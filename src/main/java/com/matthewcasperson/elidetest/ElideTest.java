package com.matthewcasperson.elidetest;

import com.yahoo.elide.Elide;
import com.yahoo.elide.ElideResponse;
import com.yahoo.elide.audit.Logger;
import com.yahoo.elide.audit.Slf4jLogger;
import com.yahoo.elide.core.DataStore;
import com.yahoo.elide.core.SecurityMode;
import com.yahoo.elide.datastores.hibernate5.HibernateStore;
import org.hibernate.SessionFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The rest interface to DZone and other services
 */
@RestController
public class ElideTest {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ElideTest.class);

    @Autowired
    private EntityManagerFactory emf;

    /**
     * Converts a plain map to a multivalued map
     * @param input The original map
     * @return A MultivaluedMap constructed from the input
     */
    private MultivaluedMap<String, String> fromMap(final Map<String, String> input) {
        return new MultivaluedHashMap<String, String>(input);
    }

    /**
     * All our elide operations require similar initialisation, which we perform in this method before calling
     * elideCallable with the elide object and the path that elide needs to know what it is supposed to do.
     * @param request The request
     * @param elideCallable A callback that is used to execute elide
     * @return The response to the client
     */
    private String elideRunner(final HttpServletRequest request, final ElideCallable elideCallable) {
        /*
            This gives us the full path that was used to call this endpoint.
         */
        final String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        /*
            Elide works with the Hibernate SessionFactory, not the JPA EntityManagerFactory.
            Fortunately we san unwrap the JPA EntityManagerFactory to get access to the
            Hibernate SessionFactory.
         */
        final SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);

        /*
            Elide takes a hibernate session factory
        */
        final DataStore dataStore = new HibernateStore(sessionFactory);

        /*
            Define a logger
         */
        final Logger logger = new Slf4jLogger();

        /*
            Create the Elide object
         */
        final Elide elide = new Elide(logger, dataStore);

        /*
            There is a bug in Elide on Windows that will convert a leading forward slash into a backslash,
            which then displays the error "token recognition error at: '\\'".
         */
        final String fixedPath = restOfTheUrl.replaceAll("^/", "");

        /*
            Now that the boilerplate initialisation is done, we let the caller do something useful
         */
        return elideCallable.call(elide, fixedPath);
    }

    @CrossOrigin(origins = "*")
    @RequestMapping(
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE,
            value={"/{entity}", "/{entity}/{id}/relationships/{entity2}", "/{entity}/{id}/{child}", "/{entity}/{id}"})
    @Transactional
    public String jsonApiGet(
            @RequestParam final Map<String, String> allRequestParams,
            final HttpServletRequest request,
            final Principal principal) {
        /*
            Here we pass through the data Spring has provided for us in the parameters, then making
            use of Java 8 Lambdas to do something useful.
         */
        return elideRunner(
                request,
                (elide, path) -> elide.get(path, fromMap(allRequestParams), principal).getBody());
    }

    @CrossOrigin(origins = "*")
    @RequestMapping(
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE,
            value={"/{entity}", "/{entity}/{id}/relationships/{entity2}"})
    @Transactional
    public String jsonApiPost(
            @RequestBody final String body,
            final HttpServletRequest request,
            final Principal principal) {
        /*
            There is not much extra work to do here over what we have already put in place for the
            get request. Our callback changes slightly, but we are still just passing objects
            from Spring to Elide.
         */
        return elideRunner(
                request,
                (elide, path) -> elide.post(path, body, principal, SecurityMode.SECURITY_INACTIVE).getBody());
    }
}
