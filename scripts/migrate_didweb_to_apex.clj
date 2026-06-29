#!/usr/bin/env bb
;;
;; migrate_didweb_to_apex.clj — Migrate existing actor DIDs from GitHub Pages
;; format to apex Worker format.
;;
;; Before: did:web:etzhayyim.github.io:com-etzhayyim-<name>
;; After:  did:web:etzhayyim.com:actor:<name>
;;
;; Updates:
;;   1. Each com-etzhayyim-<name>/.well-known/did.json — rewrite `id` to apex
;;      format, keep old id in `alsoKnownAs`.
;;   2. Each 80-data/kotoba-rad/<name>.identity.journal.edn — append a new
;;      `:rad/did-web` entry with the apex format (append-only journal).
;;
;; Usage:
;;   bb scripts/migrate_didweb_to_apex.clj              # migrate all
;;   bb scripts/migrate_didweb_to_apex.clj --dry-run     # print plan
;;   bb scripts/migrate_didweb_to_apex.clj --name toritsugi,tasuke  # subset
;;   bb scripts/migrate_didweb_to_apex.clj --check       # verify, exit 1 on drift
;;
;; Skip list: actors already on apex format, or special cases (root, kotoba).

(ns migrate-didweb-to-apex
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]
            [babashka.cli :as cli]))

(def ROOT-PATH
  (let [here (.getCanonicalPath (io/file *file*))
        scripts-dir (.getParentFile (io/file here))
        toritsugi-repo (.getParentFile scripts-dir)
        etzhayyim-dir (.getParentFile toritsugi-repo)
        orgs-dir (.getParentFile etzhayyim-dir)]
    (.getParentFile orgs-dir)))

(def ETZHAYYIM-DIR
  (io/file ROOT-PATH "orgs/etzhayyim"))

(def RAD-DIR
  (io/file ETZHAYYIM-DIR "root/80-data/kotoba-rad"))

(def SKIP-NAMES
  #{"root" "kotoba"})

(def OLD-PREFIX "did:web:etzhayyim.github.io:com-etzhayyim-")
(def NEW-PREFIX "did:web:etzhayyim.com:actor:")

(defn actor-name-from-did [did]
  (when (str/starts-with? did OLD-PREFIX)
    (str/replace did OLD-PREFIX "")))

(defn old-did->new-did [did]
  (if-let [name (actor-name-from-did did)]
    (str NEW-PREFIX name)
    did))

(defn list-actors []
  (->> (.listFiles ETZHAYYIM-DIR)
       (filter #(.isDirectory %))
       (map #(.getName %))
       (filter #(str/starts-with? % "com-etzhayyim-"))
       (map #(str/replace % "com-etzhayyim-" ""))
       (remove #(contains? SKIP-NAMES %))
       sort))

(defn read-did-json [name]
  (let [f (io/file ETZHAYYIM-DIR (str "com-etzhayyim-" name) ".well-known" "did.json")]
    (when (.exists f)
      {:file f :data (-> f slurp (json/parse-string true))})))

(defn migrate-did-json! [name dry-run?]
  (let [did-info (read-did-json name)]
    (if-not did-info
      {:name name :action :skip :reason "no .well-known/did.json"}
      (let [{:keys [file data]} did-info
            old-id (:id data)
            new-id (old-did->new-did old-id)]
        (if (= old-id new-id)
          {:name name :action :skip :reason "already apex or unknown format"}
          (let [updated (-> data
                            (assoc :id new-id)
                            (update :alsoKnownAs #(vec (distinct (conj (or % []) old-id)))))]
            (when-not dry-run?
              (spit file (json/generate-string updated {:pretty true})))
            {:name name :action (if dry-run? :planned :migrated)
             :old old-id :new new-id}))))))

(defn read-rad-journal [name]
  (let [f (io/file RAD-DIR (str name ".identity.journal.edn"))]
    (when (.exists f)
      {:file f :lines (str/split-lines (slurp f))})))

(defn migrate-rad-journal! [name dry-run?]
  (let [rad-info (read-rad-journal name)]
    (if-not rad-info
      {:name name :action :skip :reason "no RAD journal"}
      (let [{:keys [file lines]} rad-info
            old-did (str OLD-PREFIX name)
            new-did (str NEW-PREFIX name)
            already-migrated (some #(and (str/includes? % new-did)
                                          (str/includes? % ":rad/did-web"))
                                    lines)]
        (if already-migrated
          {:name name :action :skip :reason "RAD already has apex did:web"}
          (let [placeholder-cid "bafyreimigrated00000000000000000000000000000000000000000000"]
            (when-not dry-run?
              (spit file
                    (str (str/join "\n" lines) "\n"
                         "[\"" placeholder-cid "\" :rad/did-web \"" new-did "\" 2 :add]\n")))
            {:name name :action (if dry-run? :planned :migrated)
             :old old-did :new new-did}))))))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:coerce {:name :string}})
        dry-run? (:dry-run opts)
        check? (:check opts)
        name-filter (:name opts)
        all-actors (list-actors)
        actors (if name-filter
                (filter #(contains? (set (str/split name-filter #",")) %)
                        all-actors)
                all-actors)]
    (println (str "did:web apex migration — "
                  (count actors) " actor(s)"
                  (when name-filter (str " (filtered: " name-filter ")"))
                  (when dry-run? " [DRY-RUN]")))
    (println)
    (let [results (doall
                    (for [name actors]
                      (let [did-res (migrate-did-json! name dry-run?)
                            rad-res (migrate-rad-journal! name dry-run?)]
                        (println (str "  " name
                                     " — did.json: " (:action did-res)
                                     " / RAD: " (:action rad-res)
                                     (when (= :migrated (:action did-res))
                                       (str " (" (:old did-res) " → " (:new did-res) ")"))))
                        {:name name :did did-res :rad rad-res})))]
      (let [migrated (count (filter #(= :migrated (:action (:did %))) results))
            skipped (count (filter #(= :skip (:action (:did %))) results))
            rad-migrated (count (filter #(= :migrated (:action (:rad %))) results))]
        (println)
        (println (str "did.json: " migrated " migrated, " skipped " skipped"))
        (println (str "RAD:     " rad-migrated " migrated, "
                     (- (count results) rad-migrated) " skipped"))
        (println (str "Total:   " (count actors) " actor(s) processed"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
