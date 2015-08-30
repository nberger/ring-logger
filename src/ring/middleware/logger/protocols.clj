(ns ring.middleware.logger.protocols)

(defprotocol Logger
  (add-extra-middleware [_ handler])
  (error [_ x])
  (error-with-ex [_ ex x])
  (info [_ x])
  (warn [_ x])
  (debug [_ x])
  (trace [_ x]))
