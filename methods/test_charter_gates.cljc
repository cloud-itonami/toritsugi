(ns toritsugi.methods.test-charter-gates
  "toritsugi 取次 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). toritsugi is the citizen-side
  government/municipal-procedure CONCIERGE: it guides a consenting member through their OWN
  procedure, member-self-submission is the default, and the 行政書士法/UPL boundary is the
  critical gate. Its 15 gates are declared in the manifest `constitutionalGates` and encoded
  structurally across the 6 central AT-Proto lexicons at
  00-contracts/lexicons/com/etzhayyim/toritsugi/. This suite pins them so a future R-phase cell
  wave cannot silently drift them:

    G3  consent-gated (submission / benefit-match require a consentRef)
    G4  identity-bound — the member is the named 申請者本人 (every member record requires memberDid)
    G5  行政書士法 / UPL boundary — assistMode is `input-assist` ONLY (NEVER 作成代理/draft-for-member)
    G6  PII confidentiality — the draft body is an encrypted ref, never plaintext on MST
    G8  non-fabrication — a procedure must cite legalBasis + provenance (no invented 手続き/根拠)
    G10 lawful-channel-only — submission channel ⊆ {online, in-person, postal} (official channels)
    G14 verified-procedure-only — verificationStatus is the 3-tier set; unverified-seed exists
        to be REFUSED at submit
    G15 member-self-submission default — mode is exactly {member-self-submit, agent-on-behalf}
        (代行 is the single gated exception, not a silent third path)

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key (G7) nor Murakumo-only (its own G7) — the
  manifest already pins Murakumo-only inference and toritsugi holds no key."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; toritsugi/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/toritsugi"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (json/parse-string (slurp (java.io.File. actor-dir "manifest.jsonld"))))))

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- prop-keys [doc] (set (keys (get (record-node doc) "properties"))))
(defn- known-vals [doc field]
  (set (get-in (record-node doc) ["properties" field "knownValues"])))

;; ── 15 gates declared (manifest dict, keys G1…G15) ──
(deftest all-15-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])
        gates (or gates (get (manifest) "constitutionalGates"))
        nums  (->> (keys gates)
                   (keep #(second (re-matches #"G(\d+).*" %)))
                   (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 16)) nums) "manifest must declare G1–G15")))

;; ── G5 — 行政書士法 / UPL boundary: input-assist ONLY, never 作成代理 ──
(deftest g5-upl-input-assist-only
  (is (= #{"input-assist"} (known-vals (lex "applicationDraft") "assistMode"))
      "G5: applicationDraft.assistMode must be input-assist ONLY (no 作成代理/draft-for-member)"))

;; ── G15 — member-self-submission default; 代行 is the single gated exception ──
(deftest g15-self-submission-default
  (is (= #{"member-self-submit" "agent-on-behalf"} (known-vals (lex "submissionRecord") "mode"))
      "G15: submissionRecord.mode must be exactly {member-self-submit, agent-on-behalf}")
  (is (contains? (prop-keys (lex "submissionRecord")) "councilGateRef")
      "G15: the agent-on-behalf path must carry a councilGateRef (gated exception)"))

;; ── G14 — verified-procedure-only: 3-tier verification status, unverified-seed exists to refuse ──
(deftest g14-verified-procedure-only
  (let [p (lex "procedure")]
    (is (contains? (required-of p) "verificationStatus") "G14: procedure must require verificationStatus")
    (is (contains? (required-of p) "lastVerified") "G14: procedure must require lastVerified")
    (is (= #{"unverified-seed" "maintainer-verified" "council-verified"}
           (known-vals p "verificationStatus"))
        "G14: verificationStatus is the 3-tier set (unverified-seed must be refusable at submit)")))

;; ── G8 — non-fabrication: a procedure cites its legal basis + provenance ──
(deftest g8-non-fabrication
  (let [r (required-of (lex "procedure"))]
    (is (contains? r "legalBasis") "G8: procedure must cite legalBasis")
    (is (contains? r "provenance") "G8: procedure must carry provenance")))

;; ── G10 — lawful-channel-only: official channels, no scraping/automation channel ──
(deftest g10-lawful-channel-only
  (is (= #{"online" "in-person" "postal"} (known-vals (lex "submissionRecord") "channel"))
      "G10: submissionRecord.channel must be official channels only"))

;; ── G3 consent-gated + G6 PII-encrypted ──
(deftest g3-consent-g6-encrypted
  (doseq [n ["submissionRecord" "benefitMatch"]]
    (is (contains? (required-of (lex n)) "consentRef")
        (str "G3: " n " must require consentRef")))
  (is (contains? (required-of (lex "applicationDraft")) "encryptedDraftRef")
      "G6: applicationDraft body must be an encrypted ref (no plaintext draft)")
  (is (not (contains? (prop-keys (lex "applicationDraft")) "draftBody"))
      "G6: no plaintext draft body field representable"))

;; ── G4 — identity-bound: every member-facing record names the member by DID ──
(deftest g4-identity-bound
  (doseq [n ["procedureGuide" "applicationDraft" "submissionRecord" "statusTrack" "benefitMatch"]]
    (is (contains? (required-of (lex n)) "memberDid")
        (str "G4: " n " must require memberDid (member = the named 申請者本人)"))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'toritsugi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
