ring.middleware.logger
======================

Ring middleware to log the duration and other details of each request.

The logging backend is pluggable, and defaults to clojure.tools.logging
if none is given.

This is beta-level software. A number of people are using it in the
wild. API changes are unlikely at this point. Bugs are possible. Pull
requests are welcome!

Usage
-----

In your `project.clj`, add the following dependency:

```clojure
    [ring.middleware.logger "0.5.0"]
```


Then, just add the middleware to your stack. It comes preconfigured with
reasonable defaults, which append ANSI colorized log messages on each
request to whatever logger is in use by clojure.tools.logging.

```clojure
    (ns foo
      (:require [ring.adapter.jetty     :as jetty]
                [ring.middleware.logger :as logger]))

    (defn my-ring-app [request]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :body "Hello world!"})

    (jetty/run-jetty (logger/wrap-with-logger my-ring-app) {:port 8080})
```


If you'd prefer plaintext logging without the ANSI colors, use
`wrap-with-plaintext-logger` instead.


Logging only certain requests
-----------------------------

If you wish to restrict logging to certain paths (or other
conditions), combine ring.middleware.logger with its companion
project,
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
        :info (fn [x] (my.custom.logging/info x))
        :debug (fn [x] (my.custom.logging/debug x)))
```


What Gets Logged
----------------

The default setup logs:

* an :info-level message when a request begins;
* an :info level message when a response is generated without any server
errors (i.e. its HTTP status is < 500);
* an :error level message when a response's HTTP status is >= 500;
* an :error level message with a stack trace when an exception is thrown during response generation.

All messages are timestamped. Each request is assigned a random
4-hex-digit ID, so that different log messages pertaining to the same
request can be cross-referenced. These IDs are printed in random ANSI colors
by default, for easy visual correlation of log messages while reading
a log file.


Log Levels
----------
The logger logs at `INFO` level by default. More verbose information is logged when the logger is at `DEBUG` level. 

Ring.middleware.logger uses
[OneLog](https://github.com/pjlegato/onelog) internally, so we can use
OneLog's convenience methods to change the log level:


```clojure
(onelog.core/set-debug!)
(onelog.core/set-info!)
(onelog.core/set-warn!)
```


Example Log
-----------

This is an example of logging at DEBUG level. `af82` is the random ID
assigned to this particular web request. The actual ID number output
is ANSI-colorized for easy visual correlation of information related
to a given request.

````
2014-09-25 01:46:47,328 (worker-1) [INFO] : (af82) Starting :get /favicon.ico for 127.0.0.1 {"host" "localhost:8090", "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9) AppleWebKit/___.__ (KHTML, like Gecko) Chrome/--.-.----.--- Safari/---.--", "cookie" "ring-session=12345678-1234-1234-1234-1234567890abc", "connection" "keep-alive", "if-modified-since" "Wed, 24 Sep 2014 02:21:59 +0000", "accept" "*/*", "accept-language" "en-US", "accept-encoding" "gzip,deflate,sdch", "dnt" "1"}
2014-09-25 01:46:47,328 (worker-1) [DEBUG] : (af82) Request details: {:character-encoding "utf8", :content-length 0, :request-method :get, :scheme :http, :query-string nil, :uri "/favicon.ico", :remote-addr "127.0.0.1", :server-name "localhost", :server-port 8090}
limefog.log.2014-09-25:2014-09-25 01:46:47,330 (worker-1) [INFO] : (af82) Finished :get /favicon.ico for 127.0.0.1 in (3 ms) Status: 304
````


License
-------
Copyright (C) 2012-2014 Paul Legato.
Distributed under the Eclipse Public License, the same as Clojure.
