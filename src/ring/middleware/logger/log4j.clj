(ns ring.middleware.logger.log4j
  (:require [onelog.core :as log]
            [ring.middleware.logger.protocols :refer [Logger]]))

(defrecord OnelogLogger []
  Logger

  (error [_ x] (log/error x))
  (error [_ ex x] (log/error (log/throwable ex) x))
  (info [_ x] (log/info x)) 
  (warn [_ x] (log/warn x)) 
  (debug [_ x] (log/debug x))
  (trace [_ x] (log/trace x)))

(defn make-onelog-logger []
  (OnelogLogger.))
