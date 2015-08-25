(ns ring.middleware.logger.protocols)

(defprotocol Logger
  (error [_ x] [_ ex x])
  (info [_ x])
  (warn [_ x])
  (debug [_ x])
  (trace [_ x]))
