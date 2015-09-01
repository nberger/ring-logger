(ns ring.logger.protocols)

(defprotocol Logger
  (add-extra-middleware [_ handler])
  (log [_ level throwable message]))

(defn error
  ([logger throwable message] (log logger :error throwable message))
  ([logger message]           (log logger :error nil message)))
(defn info [logger message]  (log logger :info nil message))
(defn warn [logger message]  (log logger :warn nil message))
(defn debug [logger message] (log logger :debug nil message))
(defn trace [logger message] (log logger :trace nil message))
