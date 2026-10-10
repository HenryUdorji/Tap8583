/** @type {import('next').NextConfig} */
const nextConfig = {
  // Self-contained server bundle for the Docker image (see webApp/Dockerfile).
  output: "standalone",
  poweredByHeader: false,
};

export default nextConfig;
