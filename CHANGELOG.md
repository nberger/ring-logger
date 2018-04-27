# Changelog

## 1.0.1

### Fixes

  - Fix on wrap-log-request-params: StackOverflowException when passing handler only - #42 (@yanatan16)

## 1.0.0

### This is a major, breaking-changes release

- Logger protocol and messages multimethod ("printer") were replaced with `:transform-fn` and `:log-fn` options
- No default coloring (coloring can be added through the `transform-fn`)
- "Logs as data": Log messages are simple clojure maps now. This makes it easy to transform to different
  final representations: string, colored string, JSON, EDN, etc.
  
Example:

```clojure
  (require '[clansi.core :as ansi])
  (require '[timbre.core :as timbre])
  (require '[ring.logger :refer [wrap-with-logger]])
  (-> handler
      (wrap-with-logger {:log-fn (fn [{:keys [level throwable message]}]
                                   ;; log using timbre instead of clojure.tools.logging
                                   (timbre/log level throwable message))
                         :transform-fn (fn [log-item]
                                         (if (get-in log-item [:message :status])
                                           ;; colorize the status code
                                           (update-in log-item
                                                      [:message :status]
                                                      (fn [status]
                                                        (apply ansi/style
                                                          (str status)
                                                          (cond
                                                            (< status 300)  [:default]
                                                            (>= status 500) [:bright :red]
                                                            (>= status 400) [:red]
                                                            :else           [:yellow]))))
                                           log-item))}))
```

### New features

- Apart from `wrap-with-logger`, 3 additional middlewares are provided to make it easier to choose what to log:
  * `wrap-log-request-start`: logs the arrival of a request, adds `:ring.logger/start-ms` key to the request map
  * `wrap-log-request-params`: logs the parameters, using redaction to hide sensitive data
  * `wrap-log-response`: logs the response time and status, along with other data to identify the request. Uses
    `:ring.logger/start-ms` from the request map or calls `(System/currentTimeMillis)` by itself.

## 0.7.8

### Enhancements

* Add option to exclude query-string from being logged - #29 (@pmensik)
* Add asynchronous versions of the middleware - #26 (@grinderrz)

## 0.7.7

### Bug fixes

* Remove unnecessary println line - #22 - Thanks @singen!
* Avoid reflection warning - #20 - Thanks @mtkp!

## 0.7.6

### Enhancements

* Redact cookies (because sensitive information might be in cookies) #19

### Breaking changes

* `:redact-keys` accepts only keywords now. Strings are not accepted anymore.

## 0.7.5

### New features

* Add ability to redact headers & params for logging

### Dependencies

* Bump down clojure dependency to 1.6.0

## 0.7.4

### Bug fixes

* Fix timing info in exception message

## 0.7.3

### Bug fixes

* Fix wrap-with-body-logger. It wasn't working at all.

## 0.7.2

### New Features

* Add `:exceptions` option to disable exception logging.
  Useful when already using ring.middleware.stacktrace

### Enhancements

* Generate one log message instead of two when an exception occurs.
* Include colorized exception message in logged string.

## 0.7.1

New features:

* Add `:timing` option to disable logging timing information

Breaking changes:

* The options `:pre-logger`, `:post-logger` and `:exception-logger` were removed
  They were intended as a way to override the way the messages are generated,
  but we now have multimethods and `:printer` to do that.



## 0.7.0

The goal for this release was to refactor ring-logger and remove the timbre & onelog dependencies.

Breaking changes:

* Remove timbre implementation, was moved to ring-logger-timbre
* Remove OneLog implementation, was moved to ring-logger-onelog
* Replace keyword arguments with a proper map in wrap-with-logger
* Replace :logger-impl param to :logger
* Make Logger protocol more simple: It has just two fns now: `log` and `add-extra-middleware`

## 0.6.2

* Fix OneLog implementation: error -> error-with-ex

## 0.6.1

* Add ability to customize messages & to disable coloring

## 0.6.0

* Adds tools.logging and taoensso/timbre implementations, with the ability to change the
implementation by passing a :logger-impl. The default is tools.logging now
