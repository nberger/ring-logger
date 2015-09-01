This project was forked from [pjlegato/ring.middleware.logger](http://github.com/pjlegato/ring.middleware.logger)

ring-logger [![Circle CI](https://circleci.com/gh/nberger/ring-logger.svg?style=svg)](https://circleci.com/gh/nberger/ring-logger)
======================

Ring middleware to log the duration and other details of each request.

The logging backend is pluggable, included implementations: tools.logging (default) and taoensso/timbre.

[![Clojars Project](http://clojars.org/ring-logger/latest-version.svg)](http://clojars.org/ring-logger)


Migration from ring.middleware.logger (or if you just want to use some OneLog goodies)
-------------------------------------

Check out [ring-logger-onelog](https://github.com/nberger/ring-logger-onelog)

Usage
-----

In your `project.clj`, add the following dependency:

```clojure
    [ring-logger "0.6.1"]
```


Then, just add the middleware to your stack. It comes preconfigured with
reasonable defaults, which append ANSI colorized log messages on each
request to whatever logger is in use by clojure.tools.logging.

```clojure
    (ns foo
      (:require [ring.adapter.jetty     :as jetty]
                [ring.logger :as logger]))

    (defn my-ring-app [request]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :body "Hello world!"})

    (jetty/run-jetty (logger/wrap-with-logger my-ring-app) {:port 8080})
```


Usage with timbre
-----------------

In your `project.clj`, add the following dependency:

```clojure
    [ring-logger "0.6.1"]
    [com.taoensso/timbre "4.1.1"]
```


Add the middleware to your stack, using the timbre implementation. It's similar to
using the default tools.logging implementation, but passing the timbre impl when
adding the middleware:

```clojure
    (ns foo
      (:require [ring.adapter.jetty :as jetty]
                [ring.logger :as logger]
                [ring.logger.timbre :refer [make-timbre-logger]))

    (defn my-ring-app [request]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :body "Hello world!"})

    (jetty/run-jetty (logger/wrap-with-logger my-ring-app
                                              {:logger-impl (make-timbre-logger)}
                     {:port 8080}))
```

Logging only certain requests
-----------------------------

If you wish to restrict logging to certain paths (or other
conditions), combine ring-logger with
[ring.middleware.conditional](https://github.com/pjlegato/ring.middleware.conditional), like so:

```clojure
(:require [ring.middleware.conditional :as c :refer  [if-url-starts-with
                                                      if-url-doesnt-start-with
                                                      if-url-matches
                                                      if-url-doesnt-match]])

(def my-ring-app
   (-> handler
       (if-url-starts-with "/foo" wrap-with-logger)

        ;; Or:
        ;; (c/if some-test-fn wrap-with-logger)
        ;; etc.

       wrap-with-other-handler))
```

Consult the [ring.middleware.conditional docs](https://github.com/pjlegato/ring.middleware.conditional) for full details.


Custom Logger Backend
-----------------------

You can supply custom logger functions to `wrap-with-logger` by supplying pairs
of `:level custom-logger-fn` as additional arguments.  These will be used
instead of the default `clojure.tools.logging` functions. The default mapping
is:

```clojure
      :info  (fn [x] (clojure.tools.logging/info x))
      :debug (fn [x] (clojure.tools.logging/debug x))
      :error (fn [x] (clojure.tools.logging/error x))
      :warn  (fn [x] (clojure.tools.logging/warn x))
```

Replace these functions with whatever logging facility you'd like to use. Each
function should take a string and log it at that log level.  For example, if
you want to use a different function to log info and debug messages, you could
call `wrap-with-logger` like this:

```clojure
      (wrap-with-logger my-ring-app
        {:info (fn [x] (my.custom.logging/info x))
         :debug (fn [x] (my.custom.logging/debug x))})
```

Another possibility is to provide a :logger-impl instance that implements
the ring.logger.protocols/Logger protocol.


What Gets Logged
----------------

The default setup logs:

* an :info-level message when a request begins;
* an :info level message when a response is generated without any server
errors (i.e. its HTTP status is < 500);
* an :error level message when a response's HTTP status is >= 500;
* an :error level message with a stack trace when an exception is thrown during response generation.

All messages are timestamped.


Custom messages and how to disable coloring
-------------------------------------------

Instead of the default messages (for request start, details, exceptions, response trace) you might want to
provide your own custom messages. That's easy by supplying implementations of the printer multimethods
like `starting`, `request-details`, `exception` and others (see `ring.logger.messages` ns for more details)
and passing a `:printer` option to `wrap-with-logger`, like so:

```
(defmethod request-details :my-printer
  [{:keys [info trace] :as options} req]
  (trace (str "detailed request details: " req)
  (info (str "minimal request details: " (select-keys req [:character-encoding
                                                           :content-length
                                                           :request-method
                                                           :uri]))))

(wrap-with-logger app {:printer :my-printer})
```

A `:no-color` printer is provided, so to disable color:

```
(wrap-with-logger app {:printer :no-color})
```

Example Log
-----------

This is an example of logging at DEBUG level with OneLog. `af82` is the random ID
assigned to this particular web request. The actual ID number output
is ANSI-colorized for easy visual correlation of information related
to a given request.

````
2014-09-25 01:46:47,328 (worker-1) [INFO] : (af82) Starting :get /favicon.ico for 127.0.0.1 {"host" "localhost:8090", "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9) AppleWebKit/___.__ (KHTML, like Gecko) Chrome/--.-.----.--- Safari/---.--", "cookie" "ring-session=12345678-1234-1234-1234-1234567890abc", "connection" "keep-alive", "if-modified-since" "Wed, 24 Sep 2014 02:21:59 +0000", "accept" "*/*", "accept-language" "en-US", "accept-encoding" "gzip,deflate,sdch", "dnt" "1"}
2014-09-25 01:46:47,328 (worker-1) [DEBUG] : (af82) Request details: {:character-encoding "utf8", :content-length 0, :request-method :get, :scheme :http, :query-string nil, :uri "/favicon.ico", :remote-addr "127.0.0.1", :server-name "localhost", :server-port 8090}
limefog.log.2014-09-25:2014-09-25 01:46:47,330 (worker-1) [INFO] : (af82) Finished :get /favicon.ico for 127.0.0.1 in (3 ms) Status: 304
````

Roadmap
--------

* 0.6.x
    - [x] Keep the migration path from ring.middleware.logger as smooth as possible.
    - [x] Add support for tools.loggging and timbre, with the possibility to not bring not needed dependencies.
    - [x] Allow for more customizations (color/no-color, customize specific log messages).
    - [x] Development: Add tests, use continuous integration.

* 0.7.x
    - [x] Remove onelog if we think it doesn't needs to be in ring-logger (I mean: if the same can be done by using onelog in the client app + some customization).
    - [ ] Leave only tools.logging implementation in ring-logger, extract timbre implementation to other library.
    - [ ] Add more timing options, like an easy way to measure the time spent in middleware as opposed to the time spent in the "app". We should probably assoc the timing info into the request map.
    - [x] Use proper maps instead of keyword options.
    - [ ] Development: Add more tests.

Contributing
------------

Pull requests are welcome!

License
-------

Copyright (C) 2015 Nicolás Berger, 2012-2014 Paul Legato.

Distributed under the Eclipse Public License, the same as Clojure.
