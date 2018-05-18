(ns photosync.cron
  (:require
            [clojure.tools.logging :as log]
            [clj-http.client :as client]
            [ring.util.response :refer [redirect response]]
            [compojure.core :refer [defroutes ANY GET POST]]
            [ring.util.response :as response]
            [clojure.java.io :as io]
            [cheshire.core :as parse]
            [hyperion.api :as ds]))


(defn check
 [req]
 (log/info "Running check")
 (response "OK"))



(defroutes cron-routes
  (GET "/cron/check" [] check))


