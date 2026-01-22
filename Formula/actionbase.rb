class Actionbase < Formula
  desc "Command-line interface for Actionbase"
  homepage "https://github.com/kakao/actionbase"
  url "https://github.com/kakao/actionbase/archive/refs/tags/cli/0.0.1.tar.gz"
  sha256 "29dc4cfef2956a2b3bda43d4ad097dfbe8996f83b899ca1a7360e6ac56a14bf4"
  license "Apache-2.0"

  depends_on "go" => :build

  def install
    ldflags = %W[
      -s -w
      -X main.Version=#{version}
    ]

    cd "cli/cmd/actionbase" do
      system "go", "build", *std_go_args(ldflags: ldflags)
    end
  end

  test do
    system bin/"actionbase", "--version"
  end
end
