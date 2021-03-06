(ns intowow.core
  (:require [intowow.data :as data]
            [intowow.handler :as handler]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [luminus-migrations.core :as migrations]
            [intowow.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [chime :as chime]
            [clj-time.core :as time]
            [clj-time.periodic :as periodic]
            [intowow.sparkling :as spark]
            [mount.core :as mount])
  (:gen-class))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop}
  http-server
  :start
  (http/start
   (-> env
       (assoc :handler (handler/app))
       (update :port #(or (-> env :options :port) %))))
  :stop
  (http/stop http-server))

(mount/defstate ^{:on-reload :noop}
  repl-server
  :start
  (when-let [nrepl-port (env :nrepl-port)]
    (repl/start {:port nrepl-port}))
  :stop
  (when repl-server
    (repl/stop repl-server)))

(defn train-model []
  (future
    (log/info "model training starts at" (time/now))
    (try
      (spark/re-train)
      (println "switch to the new matrix model!")
      (catch Exception e
        (log/error "Error in model training :" e)))
    (log/info "model training ends at" (time/now))))

(defn init-spark
  "triggers timer to re-train model periodically"
  []
  (let [startdate (time/now)
        interval 30
        ev-seq (rest (periodic/periodic-seq startdate (time/seconds interval)))]
    (train-model)
    (chime/chime-at ev-seq (fn [t] (train-model)))))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (init-spark)
  (data/init-genre!)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (cond
    (some #{"init"} args)
    (do
      (mount/start #'intowow.config/env)
      (migrations/init (select-keys env [:database-url :init-script]))
      (System/exit 0))
    (some #{"migrate" "rollback"} args)
    (do
      (mount/start #'intowow.config/env)
      (migrations/migrate args (select-keys env [:database-url]))
      (System/exit 0))
    :else
    (start-app args)))
