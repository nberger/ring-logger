(ns ring.logger.compat
  (:require
   [ring.logger :as logger]))

(defn logger-0.7.0-transform-fn
  [log-item]
  (update-in log-item
             [:message]
             (fn [{:keys [::logger/type request-method uri server-name
                          ::logger/ms status params]}]
               (case type
                 :starting
                 (str "Starting " request-method " " uri " for " server-name)

                 :params
                 (str "  \\ - - - -  Params: " (pr-str params))

                 :finish
                 (str "Finished " request-method " " uri " for " server-name
                      " in (" ms " ms)"
                      " Status: " status)))))
