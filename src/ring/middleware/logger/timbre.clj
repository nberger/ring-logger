(ns ring.middleware.logger.timbre
  (:require [taoensso.timbre :as log]
            [ring.middleware.logger.protocols :refer [Logger]]))

(defrecord TimbreLogger []
  Logger

  (add-extra-middleware [_ handler] handler)

  (error [_ x] (log/error x))
  (error-with-ex [_ ex x] (log/error ex x))
  (info [_ x] (log/info x))
  (warn [_ x] (log/warn x)) 
  (debug [_ x] (log/debug x))
  (trace [_ x] (log/trace x)))

(defn make-timbre-logger []
  (TimbreLogger.))
