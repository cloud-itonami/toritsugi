(ns toritsugi.social-test
  (:require [clojure.test :refer [deftest is]]
            [toritsugi.cells.social-post.state-machine :as cell]
            [toritsugi.methods.social :as social]))

(deftest dry-run-procedure-projection
  (let [post (social/draft-procedure-post
              {"procedureId" "jp-juminhyo-utsushi"
               "title" "住民票の写し交付請求"
               "authority" "市区町村"
               "legalBasis" "住民基本台帳法"
               "verificationStatus" "maintainer-verified"
               "requiredDocuments" ["本人確認書類"]
               "channelType" "in-person"}
              ["https://example.go.jp/law" "https://example.go.jp/procedure"])]
    (is (= ":dry-run" (get post ":post/status")))
    (is (false? (get post ":post/server-held-key")))
    (is (true? (get post ":post/is-mirror")))))

(deftest social-boundaries-refuse
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs ≥2"
                        (social/draft-procedure-post {} ["one"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"member/actor-signed"
                        (social/emit {":post/status" ":published"
                                      ":post/author" ""
                                      ":post/sources" ["a" "b"]})))
  (is (= cell/phase-refused
         (get-in (cell/transition-to-drafted
                  {"sources" ["a" "b"] "requested_status" "published"})
                 ["cell_state" "phase"])))
  (is (= cell/phase-drafted
         (get-in (cell/transition-to-drafted
                  {"subject" "procedure" "sources" ["a" "b"]})
                 ["cell_state" "phase"]))))
