(ns photosync.parser
  (:require
    [hyperion.api :as ds]
    [clojure.tools.logging :as log]
    [photosync.model :as model]
    [photosync.util :as util]
    [photosync.accounts :as accounts]
    [photosync.model :refer [google-user]])
  (:refer-clojure :exclude [read]))
  ;(:import
  ;  [photosync.model GoogleUser]))

;; =============================================================================
;; Reads

(defmulti readf (fn [env k params] k))

(defmethod readf :default
  [_ k _]
  {:value {:error (str "No handler for read key " k)}})

(defn todos
  ([]
   (into [] []))) ;(map util/fake-datomic []))))
  ;([selector]
  ; (
  ;   :filter [:= :id selector])))

(defmethod readf :user
 [{:keys [user-details]} key params]
 {:value (select-keys user-details [:given_name :family_name :locale :name :link :picture :gender])})


(defmethod readf :todos/by-id
  [{:keys [query]} _ {:keys [id]}]
  {:value (todos)})

(defmethod readf :services/list
  [{:keys [user-details]} _ params]
  (let
    [accounts (accounts/get-valid-accounts (:key user-details))]
    {:value accounts}))

;; =============================================================================
;; Mutations

(defmulti mutatef (fn [env k params] k))

(defmethod mutatef :default
  [_ k _]
  {:value {:error (str "No handler for mutation key " k)}})

(defmethod mutatef 'todos/create
  [{:keys [conn]} k {:keys [:todo/title]}]
  {:value {:keys [:todos/list]}
   :action
   (fn []
     @(true conn
        [{:db/id          0
          :todo/title     title
          :todo/completed false
          :todo/created   (java.util.Date.)}]))})

(defmethod mutatef 'todo/update
  ;[env key params]
  [env key {:keys [db/id todo/completed todo/title] :as params}]
  {:value {:keys [[:todos/by-id id]]}
   :action
   (fn []
     (println completed)
     (if-let [entity ()] ;(ds/retrieve User id)]
       (let [updated (merge entity
                        (when (not= nil completed)
                         {:completed completed})
                        (when (not= nil title)
                         {:title title}))]
        (println entity)
        (println updated))
        ;(ds/fake-datomic (ds/save! updated)))
      (str "error")))})


(defmethod mutatef 'services/delete
  [{:keys [user-details]} k {:keys [key]}]
  {:value {:keys [:services/by-id key]}
   :action
   (fn []
     (if-let [record (ds/find-by-key key)]
       (if (= (:key user-details) (:owner record))
         (do
           (log/warn (str "Deleting " key " at request of " (:email user-details)))
           (ds/delete-by-key key)))))})




