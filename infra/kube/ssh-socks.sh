#!/bin/bash

# remove localhost from known_hosts
ssh-keygen -f $HOME/.ssh/known_hosts -R '[localhost]:2222' 2>/dev/null

# Test sshd with SOCKS proxy support locally using Docker

# Generate random 20 character password
PASSWORD=$(tr -dc 'a-zA-Z0-9' < /dev/urandom | head -c 20)

echo ""
echo "=========================================="
echo "SSH SOCKS Proxy Container"
echo "=========================================="
echo ""
echo "Password: ${PASSWORD}"
echo ""
echo "To use SOCKS proxy, run in a separate terminal:"
echo "  sshpass -p '${PASSWORD}' ssh -D 127.0.0.1:1090 -f -o PasswordAuthentication=yes -p 2222 root@localhost"
echo ""
echo "Or interactively (keeps connection open, will prompt for password):"
echo "  ssh -D 127.0.0.1:1090 -o PasswordAuthentication=yes -p 2222 root@localhost"
echo ""
echo "Or using sshpass for automated password (if installed):"
echo "  sshpass -p '${PASSWORD}' ssh -D 127.0.0.1:1090 -o PasswordAuthentication=yes -p 2222 root@localhost"
echo ""
echo "Then configure your browser/app to use SOCKS5 proxy:"
echo "  Host: localhost"
echo "  Port: 1090"
echo ""
echo "To test the proxy connection, run:"
echo "  ./test-socks.sh"
echo ""
echo "IMPORTANT: You MUST establish the SSH connection FIRST before"
echo "the browser can use the SOCKS proxy!"
echo ""
echo "=========================================="
echo "Starting container (interactive mode)..."
echo "Press Ctrl+C to stop the container"
echo "=========================================="
echo ""

# Start container in interactive mode
docker run --rm -it --name test-sshd -p 2222:22 alpine:latest sh -c "
  apk add --no-cache openssh openssh-server && \
  ssh-keygen -A && \
  mkdir -p /root/.ssh && \
  sed -i 's/^[^#]*PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config && \
  sed -i 's/^[^#]*PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
  sed -i 's/^[^#]*AllowTcpForwarding.*/AllowTcpForwarding yes/' /etc/ssh/sshd_config && \
  sed -i 's/^[^#]*GatewayPorts.*/GatewayPorts yes/' /etc/ssh/sshd_config && \
  echo '' >> /etc/ssh/sshd_config && \
  echo '# Custom configuration for SOCKS proxy' >> /etc/ssh/sshd_config && \
  echo 'PermitRootLogin yes' >> /etc/ssh/sshd_config && \
  echo 'PasswordAuthentication yes' >> /etc/ssh/sshd_config && \
  echo 'AllowTcpForwarding yes' >> /etc/ssh/sshd_config && \
  echo 'GatewayPorts yes' >> /etc/ssh/sshd_config && \
  echo \"root:${PASSWORD}\" | chpasswd && \
  echo '=== SSH Configuration ===' && \
  grep -E '^(PermitRootLogin|PasswordAuthentication|AllowTcpForwarding|GatewayPorts)' /etc/ssh/sshd_config | tail -4 && \
  echo '========================' && \
  echo 'sshd starting...' && \
  /usr/sbin/sshd -D -e
"
