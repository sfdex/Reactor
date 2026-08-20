#include "s26spoof/config_parser.hpp"

#include <cctype>
#include <cstddef>
#include <cstring>
#include <string>
#include <unordered_map>
#include <utility>

namespace s26spoof {
namespace {

enum class TokenType {
    kEnd,
    kError,
    kBraceOpen,
    kBraceClose,
    kBracketOpen,
    kBracketClose,
    kColon,
    kComma,
    kString,
    kNumber,
    kTrue,
    kFalse,
    kNull,
};

struct Token {
    TokenType type = TokenType::kError;
    std::string string_value;
};

class JsonReader {
public:
    explicit JsonReader(const char *src) : ptr_(src) {}

    void SkipWhitespace() {
        while (*ptr_ && (static_cast<unsigned char>(*ptr_) <= ' ' ||
                         *ptr_ == ' ' || *ptr_ == '\t' || *ptr_ == '\n' || *ptr_ == '\r')) {
            ++ptr_;
        }
    }

    bool NextToken(Token *token) {
        SkipWhitespace();
        if (*ptr_ == '\0') {
            token->type = TokenType::kEnd;
            token->string_value.clear();
            return true;
        }

        char c = *ptr_++;
        switch (c) {
            case '{': token->type = TokenType::kBraceOpen; return true;
            case '}': token->type = TokenType::kBraceClose; return true;
            case '[': token->type = TokenType::kBracketOpen; return true;
            case ']': token->type = TokenType::kBracketClose; return true;
            case ':': token->type = TokenType::kColon; return true;
            case ',': token->type = TokenType::kComma; return true;
            case '"': return ParseStringToken(token);
            case 't':
                if (std::strncmp(ptr_, "rue", 3) == 0) {
                    ptr_ += 3;
                    token->type = TokenType::kTrue;
                    return true;
                }
                return false;
            case 'f':
                if (std::strncmp(ptr_, "alse", 4) == 0) {
                    ptr_ += 4;
                    token->type = TokenType::kFalse;
                    return true;
                }
                return false;
            case 'n':
                if (std::strncmp(ptr_, "ull", 3) == 0) {
                    ptr_ += 3;
                    token->type = TokenType::kNull;
                    return true;
                }
                return false;
            case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                --ptr_;
                return ParseNumberToken(token);
            default:
                return false;
        }
    }

    bool PeekToken(Token *token) {
        const char *saved_ptr = ptr_;
        bool ok = NextToken(token);
        ptr_ = saved_ptr;
        return ok;
    }

    bool SkipValue(const Token &start_token) {
        switch (start_token.type) {
            case TokenType::kString:
            case TokenType::kNumber:
            case TokenType::kTrue:
            case TokenType::kFalse:
            case TokenType::kNull:
                return true;
            case TokenType::kBracketOpen: {
                Token tok;
                if (!PeekToken(&tok)) return false;
                if (tok.type == TokenType::kBracketClose) {
                    NextToken(&tok);
                    return true;
                }
                while (true) {
                    if (!NextToken(&tok)) return false;
                    if (!SkipValue(tok)) return false;
                    if (!NextToken(&tok)) return false;
                    if (tok.type == TokenType::kBracketClose) return true;
                    if (tok.type != TokenType::kComma) return false;
                }
            }
            case TokenType::kBraceOpen: {
                Token tok;
                if (!PeekToken(&tok)) return false;
                if (tok.type == TokenType::kBraceClose) {
                    NextToken(&tok);
                    return true;
                }
                while (true) {
                    if (!NextToken(&tok) || tok.type != TokenType::kString) return false;
                    if (!NextToken(&tok) || tok.type != TokenType::kColon) return false;
                    if (!NextToken(&tok) || !SkipValue(tok)) return false;
                    if (!NextToken(&tok)) return false;
                    if (tok.type == TokenType::kBraceClose) return true;
                    if (tok.type != TokenType::kComma) return false;
                }
            }
            default:
                return false;
        }
    }

    bool IsAtEnd() {
        SkipWhitespace();
        return *ptr_ == '\0';
    }

private:
    bool ParseStringToken(Token *token) {
        token->type = TokenType::kString;
        token->string_value.clear();

        while (*ptr_) {
            char c = *ptr_++;
            if (c == '"') {
                return true;
            }
            if (static_cast<unsigned char>(c) < 0x20) {
                return false;
            }
            if (c == '\\') {
                if (*ptr_ == '\0') return false;
                char esc = *ptr_++;
                switch (esc) {
                    case '"': token->string_value.push_back('"'); break;
                    case '\\': token->string_value.push_back('\\'); break;
                    case '/': token->string_value.push_back('/'); break;
                    case 'b': token->string_value.push_back('\b'); break;
                    case 'f': token->string_value.push_back('\f'); break;
                    case 'n': token->string_value.push_back('\n'); break;
                    case 'r': token->string_value.push_back('\r'); break;
                    case 't': token->string_value.push_back('\t'); break;
                    case 'u': {
                        unsigned int code_point = 0;
                        for (int i = 0; i < 4; ++i) {
                            char h = *ptr_++;
                            if (h >= '0' && h <= '9') {
                                code_point = (code_point << 4) | (h - '0');
                            } else if (h >= 'a' && h <= 'f') {
                                code_point = (code_point << 4) | (h - 'a' + 10);
                            } else if (h >= 'A' && h <= 'F') {
                                code_point = (code_point << 4) | (h - 'A' + 10);
                            } else {
                                return false;
                            }
                        }
                        if (code_point <= 0x7F) {
                            token->string_value.push_back(static_cast<char>(code_point));
                        } else if (code_point <= 0x7FF) {
                            token->string_value.push_back(static_cast<char>(0xC0 | ((code_point >> 6) & 0x1F)));
                            token->string_value.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
                        } else {
                            token->string_value.push_back(static_cast<char>(0xE0 | ((code_point >> 12) & 0x0F)));
                            token->string_value.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
                            token->string_value.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
                        }
                        break;
                    }
                    default:
                        return false;
                }
            } else {
                token->string_value.push_back(c);
            }
        }
        return false;
    }

    bool ParseNumberToken(Token *token) {
        token->type = TokenType::kNumber;
        token->string_value.clear();
        const char *start = ptr_;

        if (*ptr_ == '-') ++ptr_;
        if (*ptr_ == '\0' || !std::isdigit(static_cast<unsigned char>(*ptr_))) return false;
        while (*ptr_ && std::isdigit(static_cast<unsigned char>(*ptr_))) ++ptr_;

        if (*ptr_ == '.') {
            ++ptr_;
            if (*ptr_ == '\0' || !std::isdigit(static_cast<unsigned char>(*ptr_))) return false;
            while (*ptr_ && std::isdigit(static_cast<unsigned char>(*ptr_))) ++ptr_;
        }

        if (*ptr_ == 'e' || *ptr_ == 'E') {
            ++ptr_;
            if (*ptr_ == '+' || *ptr_ == '-') ++ptr_;
            if (*ptr_ == '\0' || !std::isdigit(static_cast<unsigned char>(*ptr_))) return false;
            while (*ptr_ && std::isdigit(static_cast<unsigned char>(*ptr_))) ++ptr_;
        }

        token->string_value.assign(start, ptr_ - start);
        return true;
    }

    const char *ptr_;
};

void SafeCopyString(char *dest, std::size_t dest_size, const std::string &src) {
    if (dest_size == 0) return;
    std::size_t copy_len = src.size() < (dest_size - 1) ? src.size() : (dest_size - 1);
    std::memcpy(dest, src.data(), copy_len);
    dest[copy_len] = '\0';
}

bool ParsePackageEntry(JsonReader *reader, AppConfigEntry *entry) {
    Token tok;
    if (!reader->PeekToken(&tok)) return false;
    if (tok.type == TokenType::kBraceClose) {
        reader->NextToken(&tok);
        return true;
    }

    while (true) {
        if (!reader->NextToken(&tok) || tok.type != TokenType::kString) return false;
        std::string key = tok.string_value;

        if (!reader->NextToken(&tok) || tok.type != TokenType::kColon) return false;
        if (!reader->NextToken(&tok)) return false;

        if (key == "enabled") {
            if (tok.type == TokenType::kTrue) {
                entry->enabled = true;
            } else if (tok.type == TokenType::kFalse) {
                entry->enabled = false;
            } else {
                return false;
            }
        } else if (key == "name") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->name, sizeof(entry->name), tok.string_value);
        } else if (key == "manufacturer") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->profile.manufacturer, sizeof(entry->profile.manufacturer), tok.string_value);
        } else if (key == "brand") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->profile.brand, sizeof(entry->profile.brand), tok.string_value);
        } else if (key == "model") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->profile.model, sizeof(entry->profile.model), tok.string_value);
        } else if (key == "device") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->profile.device, sizeof(entry->profile.device), tok.string_value);
        } else if (key == "product") {
            if (tok.type != TokenType::kString) return false;
            SafeCopyString(entry->profile.product, sizeof(entry->profile.product), tok.string_value);
        } else {
            if (!reader->SkipValue(tok)) return false;
        }

        if (!reader->NextToken(&tok)) return false;
        if (tok.type == TokenType::kBraceClose) return true;
        if (tok.type != TokenType::kComma) return false;
    }
}

bool ParsePackagesObject(JsonReader *reader, std::unordered_map<std::string, AppConfigEntry> *packages) {
    Token tok;
    if (!reader->PeekToken(&tok)) return false;
    if (tok.type == TokenType::kBraceClose) {
        reader->NextToken(&tok);
        return true;
    }

    while (true) {
        if (!reader->NextToken(&tok) || tok.type != TokenType::kString) return false;
        std::string pkg_name = tok.string_value;

        if (!reader->NextToken(&tok) || tok.type != TokenType::kColon) return false;
        if (!reader->NextToken(&tok) || tok.type != TokenType::kBraceOpen) return false;

        AppConfigEntry entry = {};
        if (!ParsePackageEntry(reader, &entry)) return false;

        (*packages)[pkg_name] = entry;

        if (!reader->NextToken(&tok)) return false;
        if (tok.type == TokenType::kBraceClose) return true;
        if (tok.type != TokenType::kComma) return false;
    }
}

}  // namespace

bool parse_config_json(const char *json,
                       std::unordered_map<std::string, AppConfigEntry> *out_packages) {
    if (json == nullptr || out_packages == nullptr) return false;

    JsonReader reader(json);
    Token tok;
    if (!reader.NextToken(&tok) || tok.type != TokenType::kBraceOpen) {
        return false;
    }

    std::unordered_map<std::string, AppConfigEntry> packages;

    if (!reader.PeekToken(&tok)) return false;
    if (tok.type == TokenType::kBraceClose) {
        reader.NextToken(&tok);
    } else {
        while (true) {
            if (!reader.NextToken(&tok) || tok.type != TokenType::kString) return false;
            std::string root_key = tok.string_value;

            if (!reader.NextToken(&tok) || tok.type != TokenType::kColon) return false;
            if (!reader.NextToken(&tok)) return false;

            if (root_key == "version") {
                if (tok.type != TokenType::kNumber) return false;
            } else if (root_key == "packages") {
                if (tok.type != TokenType::kBraceOpen) return false;
                if (!ParsePackagesObject(&reader, &packages)) return false;
            } else {
                if (!reader.SkipValue(tok)) return false;
            }

            if (!reader.NextToken(&tok)) return false;
            if (tok.type == TokenType::kBraceClose) break;
            if (tok.type != TokenType::kComma) return false;
        }
    }

    if (!reader.IsAtEnd()) return false;

    *out_packages = std::move(packages);
    return true;
}

}  // namespace s26spoof
