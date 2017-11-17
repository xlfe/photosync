(ns cae.model
  (:require
  ;  [[cae.services :as services]
  ;   [user :as user]
     [cae.datastore :refer [defentity]]))
  ;   ]
  ;  )



;(defn public-entity
;  [e key-id]
;  (let [keys (get-in config/app [:db key-id])]
;    (if (= :* keys) (into {} e) (select-keys e keys))))

(defentity CodeTree
           [id title author
            ^:clj tree
            tree-depth
            seed
            author-location
            status
            short-uri
            stl-uri
            video-uri
            preview-uri
            lux-uri
            created
            parent-id
            job-id]
           :key :id)

(defentity PrintJob
           [id
            object-id
            status
            created
            started
            user]
           :key :id)

(defentity PrintLog
           [id type date message]
           :key :id)