#!/usr/bin/env python3
"""Minimal RouterOS API client (stdlib only). Usage: routeros_api_cli.py HOST USER PASS /ip/firewall/filter/print"""
from __future__ import annotations

import hashlib
import socket
import sys
from typing import Any


def encode_length(length: int) -> bytes:
    if length < 0x80:
        return bytes([length])
    if length < 0x4000:
        length |= 0x8000
        return bytes([(length >> 8) & 0xFF, length & 0xFF])
    if length < 0x200000:
        length |= 0xC00000
        return bytes([(length >> 16) & 0xFF, (length >> 8) & 0xFF, length & 0xFF])
    length |= 0xE0000000
    return bytes(
        [
            (length >> 24) & 0xFF,
            (length >> 16) & 0xFF,
            (length >> 8) & 0xFF,
            length & 0xFF,
        ]
    )


def encode_word(word: str) -> bytes:
    data = word.encode("utf-8")
    return encode_length(len(data)) + data


def send_sentence(sock: socket.socket, words: list[str]) -> None:
    for word in words:
        sock.sendall(encode_word(word))
    sock.sendall(b"\x00")


def read_word(sock: socket.socket) -> str:
    length_data = sock.recv(1)
    if not length_data:
        raise ConnectionError("connection closed")
    length = length_data[0]
    if length & 0x80:
        if (length & 0xC0) == 0x80:
            length = ((length & 0x3F) << 8) + sock.recv(1)[0]
        elif (length & 0xE0) == 0xC0:
            b = sock.recv(2)
            length = ((length & 0x1F) << 16) + (b[0] << 8) + b[1]
        elif (length & 0xF0) == 0xE0:
            b = sock.recv(3)
            length = ((length & 0x0F) << 24) + (b[0] << 16) + (b[1] << 8) + b[2]
        else:
            b = sock.recv(4)
            length = (b[0] << 24) + (b[1] << 16) + (b[2] << 8) + b[3]
    if length == 0:
        return ""
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("connection closed while reading word")
        data += chunk
    return data.decode("utf-8", errors="replace")


def read_sentence(sock: socket.socket) -> list[str]:
    words: list[str] = []
    while True:
        word = read_word(sock)
        if word == "":
            break
        words.append(word)
    return words


def login(sock: socket.socket, username: str, password: str) -> None:
    send_sentence(sock, ["/login", f"=name={username}", f"=password={password}"])
    reply = read_sentence(sock)
    if not reply or reply[0] == "!trap":
        if reply and reply[0] == "!done" and any("=ret" in w for w in reply):
            challenge = next(w.split("=", 1)[1] for w in reply if w.startswith("=ret="))
            md5 = hashlib.md5()
            md5.update(b"\x00")
            md5.update(password.encode("utf-8"))
            md5.update(bytes.fromhex(challenge))
            send_sentence(
                sock,
                [
                    "/login",
                    f"=name={username}",
                    "=response=00" + md5.hexdigest(),
                ],
            )
            reply = read_sentence(sock)
        else:
            raise RuntimeError(f"login failed: {reply}")
    if not reply or reply[0] != "!done":
        raise RuntimeError(f"login failed: {reply}")


def call(sock: socket.socket, path: str, attrs: dict[str, str] | None = None) -> list[dict[str, str]]:
    words = [path]
    if attrs:
        words.extend(f"={k}={v}" for k, v in attrs.items())
    send_sentence(sock, words)
    rows: list[dict[str, str]] = []
    while True:
        sentence = read_sentence(sock)
        if not sentence:
            continue
        tag = sentence[0]
        if tag == "!done":
            break
        if tag == "!trap":
            raise RuntimeError(str(sentence))
        if tag == "!re":
            row: dict[str, str] = {}
            for item in sentence[1:]:
                if item.startswith("="):
                    key, _, value = item[1:].partition("=")
                    row[key] = value
            rows.append(row)
    return rows


def main() -> None:
    if len(sys.argv) < 5:
        print("usage: routeros_api_cli.py HOST PORT USER PASS COMMAND [key=value ...]", file=sys.stderr)
        sys.exit(2)
    host, port_s, user, password, command, *rest = sys.argv[1:]
    attrs = {}
    for item in rest:
        key, _, value = item.partition("=")
        attrs[key] = value
    sock = socket.create_connection((host, int(port_s)), timeout=15)
    try:
        login(sock, user, password)
        rows = call(sock, command, attrs or None)
        for row in rows:
            print(row)
    finally:
        sock.close()


if __name__ == "__main__":
    main()
