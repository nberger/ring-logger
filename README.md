# Ring-logger [![Circle CI](https://circleci.com/gh/nberger/ring-logger.svg?style=svg)](https://circleci.com/gh/nberger/ring-logger)

[Ring](https://github.com/ring-clojure/ring) [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) to log
response time and other details of each request that arrives to your server.

- Logs request start, finish, parameters and exceptions by default.
- The user can choose which of those messages to log by using the more specific middleware.
- "Logs as data": Log messages are simple clojure maps. You can provide a `transform-fn` to
  transform to other representations (string, JSON).
- Uses [clojure.tools.logging](https://github.com/clojure/tools.logging) by default,
  accepts a `log-fn` for switching to other log backends ([timbre](https://github.com/ptaoussanis/timbre), etc.)

[DOCUMENTATION](https://nberger.github.io/ring-logger/doc) | [0.7.x](https://github.com/nberger/ring-logger/tree/0.7.x)

[![Clojars Project](http://clojars.org/ring-logger/latest-version.svg)](http://clojars.org/ring-logger)

## Getting started

Add the dependency to your project:

```clojure
    [ring-logger "1.0.1"]
```

## Usage

Add the middleware to your ring stack:

```clojure
    (ns foo
      (:require [ring.adapter.jetty :as jetty]
                [ring.logger :as logger]))

    (defn my-ring-app [request]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :body "Hello world!"})

    (jetty/run-jetty (logger/wrap-with-logger my-ring-app) {:port 8080})
```

Example output:

    INFO  ring.logger: {:request-method :get, :uri "/", :server-name "localhost", :ring.logger/type :starting}                                                                                                  
    DEBUG ring.logger: {:request-method :get, :uri "/", :server-name "localhost", :ring.logger/type :params, :params {:name "ring-logger", :password "[REDACTED]"}}                                             
    INFO  ring.logger: {:request-method :get, :uri "/", :server-name "localhost", :ring.logger/type :finish, :status 200, :ring.logger/ms 11}

## Advanced usage

ring.logger comes with more fine-grained middleware apart from `wrap-with-logger`:

- `wrap-log-request-start`: Logs the start of the request
- `wrap-log-response`
- `wrap-log-request-params`: Logs the request parameters, using redaction to hide sensitive values (passwords, tokens, etc)

To log just the start and finish of requests (no parameters):

```clojure
    (-> handler
        logger/wrap-log-response
        ;; more middleware to parse params, cookies, etc.
        logger/wrap-log-request-start)
```

To measure request latency, `wrap-log-response` will use the `ring.logger/start-ms` key added by `wrap-log-request-start`
if both middlewares are being used, or will call `System/currentTimeMillis` to obtain the value by itself.

## Using other loggging backends

Other logging backends can be plugged by passing the `log-fn` option. This is how you could use
[timbre](https://github.com/ptaoussanis/timbre) instead of c.t.logging:


```clojure
    (require '[timbre.core :as timbre])

    (-> handler
        (logger/wrap-log-response {:log-fn (fn [{:keys [level throwable message]}]
                                             (timbre/log level throwable message))}))
```

## What gets logged



* an :info level message when a request begins.
* a :debug level message with the request parameters (redacted).
* an :info level message when a response is returned without server
  errors (i.e. its HTTP status is < 500), otherwise an :error level message is logged.
* an :error level message with a stack trace when an exception is thrown during response generation.

All messages will be usually timestamped by your logging infrastructure.

## How to disable exceptions logging

This is especially useful when also using ring.middleware.stacktrace.

```clojure
(wrap-with-logger app {:log-exceptions? false})
```

## Password: "[REDACTED]"

Sensitive information in params and headers can be redacted before it's sent to
the logs.

**This is very important**: Nobody wants user passwords or authentication
tokens to get to the logs and live there forever, in plain text, *right*?

By default, ring-logger will redact any parameter whose name is in the
`ring.logger/default-redact-key?` set (:password, :token, :secret, etc).
You can pass your own set or function to determine which keys to redact
as the `redact-key?` option

```clojure
(wrap-with-logger app {:redact-key? #{:senha :token})
```

## Logging only certain requests

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

## Similar projects

[pjlegato/ring.middleware.logger](http://github.com/pjlegato/ring.middleware.logger): ring-logger started as a fork
of ring.middleware.logger. It's a great option if you don't mind pulling a transitive dependency on onelog & log4j.

[lambdaisland/ring.middleware.logger](https://github.com/RadicalZephyr/ring.middleware.logger): a fork of r.m.logger
that replaces onelog with clojure.tools.logging

## Contributing

Pull requests are welcome!

## License

Copyright (C) 2015-2018 Nicolás Berger
Copyright (C) 2012-2014 Paul Legato.

Distributed under the Eclipse Public License, the same as Clojure.
