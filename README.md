ring.middleware.logger
======================

Ring middleware to log each request using Log4J.

Usage
-----

In your `project.clj`, add the following dependency:

                 [ring.middleware.logger "0.1.0"]


Then, just add the middleware to your stack. It comes preconfigured with
reasonable defaults, which append ANSI colorized log messages on each request to `logs/ring.log`.

In Noir, setup is as simple as:

    (ns my.web.server
      (:require
         [noir.server]
         [ring.middleware.logger :as logger]))

    (defonce logger-middleware (nr-server/add-middleware logger/wrap-with-logger))
    ;; Then start the server normally

You'll then see messages like the following in `logs/ring.log` (in color):

    2012-05-23 07:16:11,890 [INFO] : [Status: 200] :get /count (127.0.0.1)  (28 ms)
    2012-05-23 07:16:12,042 [INFO] : [Status: 404] :get /css/default.css (127.0.0.1)  (9 ms)
    2012-05-23 07:16:12,045 [INFO] : [Status: 200] :get /css/reset.css (127.0.0.1)  (3 ms)
    2012-05-23 07:16:12,323 [INFO] : [Status: 404] :get /favicon.ico (127.0.0.1)  (8 ms)

If you'd prefer plaintext logging without the ANSI colors, just use:

    (defonce logger-middleware (nr-server/add-middleware logger/wrap-with-plaintext-logger))


Customization
-------------

Every aspect of the logging can be customized, and a few presets are provided.

You can supply your own logger function and your own prefix
specification (the part that does the timestamp and log level at the
beginning of the line) for further customization. Take a look at
`logger.clj` to see what's available.

License
-------
Copyright (C) 2012 Paul Legato. 
Distributed under the Eclipse Public License, the same as Clojure.
