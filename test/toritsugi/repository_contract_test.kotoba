(ns toritsugi.repository-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(defn- read-edn [path] (edn/read-string (slurp path)))

(deftest canonical-repository-shape
  (doseq [path ["manifest.edn" "identity.edn" "dependencies.edn"
                "repository-contracts.edn" "kotoba.app.edn" "schema.edn"
                "registry/procedures.seed.edn"
                "registry/toritsugi.procedure-flow.bpmn.edn"]]
    (is (some? (read-edn path)) path))
  (is (= "toritsugi" (:actor/id (read-edn "manifest.edn"))))
  (is (= ["chigiri" "ooyake"] (:actor/integrates (read-edn "manifest.edn"))))
  (is (= 154 (count (filter #(.isFile %)
                            (file-seq (io/file "lex"))))))
  (is (= 154 (count (filter #(.isFile %)
                            (file-seq (io/file "wire/lexicons"))))))
  (is (not (.exists (io/file "manifest.jsonld"))))
  (is (not (.exists (io/file "run_tests.sh"))))
  (is (not (.exists (io/file "registry/procedures.seed.json"))))
  (is (.exists (io/file "wire/manifest.jsonld")))
  (is (.exists (io/file "wire/registry/procedures.seed.json"))))

(deftest dependencies-are-immutable-flat-west-references
  (let [deps (:dependencies (read-edn "dependencies.edn"))]
    (is (= 6 (count deps)))
    (is (every? #(re-matches #"[0-9a-f]{40}" (:dependency/revision %)) deps))
    (is (= #{"orgs/etzhayyim/com-etzhayyim-chigiri"
             "orgs/etzhayyim/com-etzhayyim-ooyake"
             "orgs/kotoba-lang/langgraph"
             "orgs/kotoba-lang/langchain"
             "orgs/kotoba-lang/org-omg-bpmn"}
           (set (keep :dependency/west-path deps))))))
