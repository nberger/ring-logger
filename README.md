ring.middleware.logger
======================

Ring middleware to log each request using Log4J.

This is ALPHA software; the internals and API can change at any time.

Usage
-----

In your `project.clj`, add the following dependency:

    [ring.middleware.logger "0.2.2"]


Then, just add the middleware to your stack. It comes preconfigured with
reasonable defaults, which append ANSI colorized log messages on each request to `logs/ring.log`.

In Noir, setup is as simple as:

    (ns my.web.server
      (:require
         [noir.server]
         [ring.middleware.logger :as logger]))

    (defonce logger-middleware (noir.server/add-middleware logger/wrap-with-logger))
    ;; Then start the server normally

If you'd prefer plaintext logging without the ANSI colors, just use:

    (defonce logger-middleware (nr-server/add-middleware logger/wrap-with-plaintext-logger))


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


Logging Other Data
------------------

By default, the log backend is set up to log only messages generated
from the ring.middleware.logger namespace. You can optionally use the
same log facility used by the logger middleware to log any other data
you like.

If you want your entire app to log to the ring.middleware.logger backend, call:

     (logger/set-default-root-logger!)

If you only want a particular namespace to be directed to this backend, call

     (logger/set-default-logger! "some.namespace")
     (logger/set-default-logger!) ;; auto-uses the namespace of the calling context

Then, you can use `clojure.tools.logging` functions normally, e.g.

    (clojure.tools.logging/error "Some error message")
    (clojure.tools.logging/info "An informational message")

Customization
-------------

Every aspect of the logging can be customized, and a few presets are provided.

You can supply your own logger function and your own prefix
specification (the part that does the timestamp and log level at the
beginning of the line) for further customization. Take a look at
`logger.clj` to see what's available.

License
-------
ring.middleware.logger is by Paul Legato.
Copyright (C) 2012 Spring Semantics Inc.
Distributed under the Eclipse Public License, the same as Clojure.
