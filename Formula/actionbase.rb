class Actionbase < Formula
  desc "actionbase CLI"
  homepage "https://github.com/kakao/actionbase"
  version "0.0.1"
  license "Apache-2.0 license"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/kakao/actionbase/releases/download/cli/#{version}/actionbase_latest_darwin_arm64.tar.gz"
      sha256 "0ac3b042eff3e3dcd5e1d299815d4782149fe6e17039b461783ee3cd29fe6d88"
    else
      url "https://github.com/kakao/actionbase/releases/download/cli/#{version}/actionbase_latest_darwin_amd64.tar.gz"
      sha256 "b96e8967a91109830ab1cbd90f567cdf0e24fd9bc486b91c753df91b7065e4eb"
    end
  end

  on_linux do
    url "https://github.com/kakao/actionbase/releases/download/cli/#{version}/actionbase_latest_linux_amd64.tar.gz"
    sha256 "b2e8f88534f417099892025209b68a389e1abe24e6883a08cfed24decb4cf939"
  end

  def install
    bin.install "actionbase"
  end

  test do
    system "#{bin}/actionbase", "--version"
  end
end
