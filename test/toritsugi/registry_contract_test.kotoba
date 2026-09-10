(ns toritsugi.registry-contract-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]))

(def seed (edn/read-string (slurp "registry/procedures.seed.edn")))
(def procedures (get seed "procedures"))

(deftest worldwide-registry-census
  (is (= 164 (count procedures)))
  (is (= 148 (count (set (map #(get % "regime") procedures)))))
  (is (= 52 (count (set (map #(get % "jurisdiction") procedures)))))
  (is (= (count procedures)
         (count (set (map #(get % "procedureId") procedures))))))

(deftest g14-seed-is-non-live-and-source-cited
  (is (every? #(= "unverified-seed" (get % "verificationStatus")) procedures))
  (is (every? #(re-matches #"https://.+" (get % "provenance")) procedures)))

(deftest bpmn-edn-is-executable-and-gated
  (let [entity (first (edn/read-string
                       (slurp "registry/toritsugi.procedure-flow.bpmn.edn")))
        nodes (edn/read-string (:bpmn/nodes entity))
        flows (edn/read-string (:bpmn/flows entity))]
    (is (true? (:bpmn/executable entity)))
    (is (= "F_self" (get-in nodes ["mode_gw" :bpmn/default])))
    (is (= :user-task (get-in nodes ["approval" :bpmn/type])))
    (is (re-find #"agent-on-behalf" (get-in flows ["F_agent" :bpmn/condition])))))
