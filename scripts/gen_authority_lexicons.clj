#!/usr/bin/env bb
;;
;; gen_authority_lexicons.clj — toritsugi authority-actor lexicon generator
;;
;; Reads registry/procedures.seed.json, groups by `regime`, and emits one
;; lexicon per regime at 00-contracts/lexicons/com/etzhayyim/toritsugi/<regime>/procedure.json
;;
;; Usage:
;;   bb scripts/gen_authority_lexicons.clj                  # generate all
;;   bb scripts/gen_authority_lexicons.clj --dry-run         # print plan
;;   bb scripts/gen_authority_lexicons.clj --regime jp-jichitai,jp-national
;;   bb scripts/gen_authority_lexicons.clj --check           # verify, exit 1 on drift
;;
;; Each lexicon = parent `com.etzhayyim.toritsugi.procedure` schema with
;; the `regime` knownValues pinned to a single value (the regime itself).

(ns gen-authority-lexicons
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

(def SEED-PATH
  (io/file ROOT-PATH "orgs/etzhayyim/com-etzhayyim-toritsugi/registry/procedures.seed.json"))

(def LEXICON-DIR
  (io/file ROOT-PATH "orgs/etzhayyim/root/00-contracts/lexicons/com/etzhayyim/toritsugi"))

(defn load-seed []
  (-> SEED-PATH slurp (json/parse-string true)))

(defn regime->procs [seed]
  (->> (:procedures seed)
       (group-by :regime)
       (sort-by (fn [[regime _]] regime))
       (into [])))

(defn build-lexicon [regime procs]
  (let [lexicon-id (str "com.etzhayyim.toritsugi." regime ".procedure")
        jurisdictions (->> procs (map :jurisdiction) distinct sort)
        authorities (->> procs (map :authority) distinct sort)
        sample (first procs)]
    {"lexicon" 1
     "id" lexicon-id
     "description" (str "Authority-scoped procedure registry for regime '"
                        regime "' (" (str/join " / " authorities)
                        "). Covers " (count procs) " procedure(s) across "
                        (str/join ", " jurisdictions)
                        ". Child of com.etzhayyim.toritsugi.procedure (ADR-2605312030). "
                        "Same schema + constitutional gates (G5/G8/G14/G15). "
                        "verificationStatus gates live submission (G14). "
                        "行政書士法/UPL boundary: 情報提供+案内+入力補助 only, NO advice, NO 作成代理. "
                        "Member self-submits (G15).")
     "defs"
     {"main"
      {"type" "record"
       "key" "tid"
       "record"
       {"type" "object"
        "required" ["procedureId" "title" "jurisdiction" "authority"
                     "channelType" "legalBasis" "provenance"
                     "lastVerified" "verificationStatus"]
        "properties"
        {"procedureId" {"type" "string" "maxLength" 120
                        "description" "Stable slug (e.g. 'jp-juminhyo-utsushi')"}
         "title" {"type" "string" "maxLength" 200
                  "description" "Procedure display name"}
         "jurisdiction" {"type" "string" "maxLength" 8
                        "description" (str "e.g. " (first jurisdictions))}
         "regime" {"type" "string"
                   "knownValues" [regime]
                   "description" (str "Authority regime (pinned to '" regime
                                      "' for this lexicon).")}
         "authority" {"type" "string" "maxLength" 300
                     "description" "所管 — the ministry / municipality / agency"}
         "channelType" {"type" "string"
                       "knownValues" ["online" "in-person" "postal" "in-person-or-online"]
                       "description" "HOW the procedure is filed"}
         "onlineUrl" {"type" "string" "format" "uri"
                     "description" "Online application portal URL if any"}
         "requiredDocuments" {"type" "array"
                              "items" {"type" "string" "maxLength" 300}
                              "description" "必要書類 checklist"}
         "formRef" {"type" "string" "maxLength" 120
                   "description" "chigiri procedure-template id"}
         "feeJpy" {"type" "integer" "minimum" 0
                  "description" "手数料 in JPY (G9: authority fee, not toritsugi charge)"}
         "feeUsd" {"type" "integer" "minimum" 0 "description" "Authority fee in USD (G9)"}
         "feeEur" {"type" "integer" "minimum" 0 "description" "Authority fee in EUR (G9)"}
         "feeGbp" {"type" "integer" "minimum" 0 "description" "Authority fee in GBP (G9)"}
         "feeCad" {"type" "integer" "minimum" 0 "description" "Authority fee in CAD (G9)"}
         "feeAud" {"type" "integer" "minimum" 0 "description" "Authority fee in AUD (G9)"}
         "feeInr" {"type" "integer" "minimum" 0 "description" "Authority fee in INR (G9)"}
         "feeSgd" {"type" "integer" "minimum" 0 "description" "Authority fee in SGD (G9)"}
         "feeBrl" {"type" "integer" "minimum" 0 "description" "Authority fee in BRL (G9)"}
         "feeMxn" {"type" "integer" "minimum" 0 "description" "Authority fee in MXN (G9)"}
         "feeKrw" {"type" "integer" "minimum" 0 "description" "Authority fee in KRW (G9)"}
         "statutoryProcessingDays" {"type" "integer" "minimum" 0
                                    "description" "法定処理期間 (0 = same-day)"}
         "legalBasis" {"type" "string" "maxLength" 300
                       "description" "根拠法令 — MANDATORY (G8)"}
         "language" {"type" "string" "maxLength" 16
                    "description" "Filing language, e.g. ja / en"}
         "stateAlignedFlag" {"type" "boolean" "description" "G13 pass-through"}
         "provenance" {"type" "string" "format" "uri"
                       "description" "Source URL — MANDATORY (G8)"}
         "lastVerified" {"type" "string" "format" "datetime"
                        "description" "When the procedure data was last verified"}
         "verificationStatus" {"type" "string"
                               "knownValues" ["unverified-seed" "maintainer-verified" "council-verified"]
                               "description" "G14 submission gate"}
         "confidence" {"type" "string"
                      "knownValues" ["high" "medium" "low"]
                      "description" "Best-effort confidence (informational only)"}
         "notes" {"type" "string" "maxLength" 1500}}}}}}))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:coerce {:regime :string}})
        dry-run? (:dry-run opts)
        check? (:check opts)
        regime-filter (:regime opts)
        seed (load-seed)
        all-regimes (regime->procs seed)
        regimes (if regime-filter
                  (filter #(contains? (set (str/split regime-filter #","))
                                      (first %))
                          all-regimes)
                  all-regimes)]
    (println (str "toritsugi authority-lexicon generator — "
                  (count regimes) " regime(s)"
                  (when regime-filter (str " (filtered: " regime-filter ")"))
                  (when dry-run? " [DRY-RUN]")))
    (doseq [[regime procs] regimes]
      (let [lexicon (build-lexicon regime procs)
            out-dir (io/file LEXICON-DIR regime)
            out-file (io/file out-dir "procedure.json")]
        (if dry-run?
          (println (str "  " regime " → " (.getPath out-file) " [dry-run]"))
          (do
            (io/make-parents out-file)
            (spit out-file (json/generate-string lexicon {:pretty true}))
            (println (str "  " regime " → " (.getPath out-file)
                         " (" (count procs) " proc)"))))))
    (println)
    (println (str "Total: " (count regimes) " lexicon(s) "
                  (if dry-run? "planned" "generated")))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
