class Actionbase < Formula
  desc "actionbase CLI"
  homepage "https://github.com/kakao/actionbase"
  version "0.0.1"
  license "MIT"

  ENV["CGO_ENABLED"] = "0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_arm64.tar.gz"
      sha256 "8c48350353594828b13726f75c4ece410db2955642fc89922db75c65c8c67d03"
    else
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_amd64.tar.gz"
      sha256 "0de525edf7cc0199d8325d7a531c3293a27de8ab204b1090d1be886e3a1182f7"
    end
  end

  on_linux do
    url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_linux_amd64.tar.gz"
    sha256 "c40d839765ec32b0cc97dfccc555196a377d028c3091f82ee50f747b01b2b3bc"
  end

  def install
    bin.install "actionbase"
  end

  test do
    system "#{bin}/actionbase", "--version"
  end
end
