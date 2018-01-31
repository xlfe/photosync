(ns photosync.model
  (:require [photosync.datastore :refer [defentity]]))



;(defn public-entity
;  [e key-id]
;  (let [keys (get-in config/app [:db key-id])]
;    (if (= :* keys) (into {} e) (select-keys e keys))))

(defentity Todo
           [id
            title
            ;^:clj history
            completed
            created]
           :key :id)

