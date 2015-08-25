(ns ring.middleware.logger.protocols)

(defprotocol Logger
  (add-extra-middleware [_ f])
  (error [_ x] [_ ex x])
  (info [_ x])
  (warn [_ x])
  (debug [_ x])
  (trace [_ x]))

(extend-type Object
  Logger
  (add-extra-middleware [_ handler]
    ; returns handler untouched
    handler))
