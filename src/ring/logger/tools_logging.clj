(ns ring.logger.tools-logging
  (:require [clojure.tools.logging :as log]
            [ring.logger.protocols :refer [Logger]]))

(defrecord ToolsLoggingLogger []
  Logger

  (add-extra-middleware [_ handler] handler)
  (log [_ level throwable message]
    (log/log level throwable message)))

(defn make-tools-logging-logger []
  (ToolsLoggingLogger.))
