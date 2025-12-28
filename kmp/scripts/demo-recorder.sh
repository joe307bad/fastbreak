#!/bin/bash

# Demo Recorder - Interactive wrapper for record-ios-demo.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMOS_FILE="$SCRIPT_DIR/DemoUITests.swift"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

show_header() {
    echo -e "${CYAN}╔═══════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║      Fastbreak Demo Recorder (iOS)           ║${NC}"
    echo -e "${CYAN}╚═══════════════════════════════════════════════╝${NC}"
    echo ""
}

list_demos() {
    echo -e "${GREEN}Available Demos:${NC}"
    echo ""

    local demos=($(grep -o 'func testDemo_[a-zA-Z]*' "$DEMOS_FILE" | sed 's/func //' | sort))
    local index=1

    for demo in "${demos[@]}"; do
        local name=$(echo "$demo" | sed 's/testDemo_//' | sed 's/\([A-Z]\)/ \1/g' | sed 's/^ //')
        printf "  ${YELLOW}%2d${NC}. %s\n" "$index" "$name"
        ((index++))
    done

    echo ""
    return ${#demos[@]}
}

show_usage() {
    echo -e "${BLUE}Usage:${NC}"
    echo "  $0                    # Interactive mode"
    echo "  $0 list               # List available demos"
    echo "  $0 all                # Record all demos"
    echo "  $0 <number>           # Record demo by number"
    echo "  $0 <test-name>        # Record specific test"
    echo ""
    echo -e "${BLUE}Examples:${NC}"
    echo "  $0                                    # Interactive selection"
    echo "  $0 1                                  # Record first demo"
    echo "  $0 testDemo_PinchToZoomAndPan        # Record by test name"
    echo "  $0 all                                # Record all demos"
    echo ""
    echo -e "${BLUE}Configuration:${NC}"
    echo "  FPS=15 SCALE=800 $0 1                # High quality"
    echo "  SIMULATOR_NAME=\"iPhone 14\" $0 2    # Different simulator"
    echo ""
}

record_demo() {
    local test_name=$1
    echo -e "${GREEN}Recording: ${test_name}${NC}"
    TEST_NAME="$test_name" "$SCRIPT_DIR/record-ios-demo.sh"
}

record_all_demos() {
    local demos=($(grep -o 'func testDemo_[a-zA-Z]*' "$DEMOS_FILE" | sed 's/func //' | sort))
    echo -e "${GREEN}Recording ${#demos[@]} demos...${NC}"
    echo ""

    for demo in "${demos[@]}"; do
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        record_demo "$demo"
        echo ""
    done

    echo -e "${GREEN}✓ All demos recorded!${NC}"
}

interactive_mode() {
    show_header
    list_demos
    local count=$?

    echo -e "${YELLOW}Enter demo number (1-$count) or 'q' to quit:${NC} "
    read -r selection

    if [[ "$selection" =~ ^[0-9]+$ ]] && [ "$selection" -ge 1 ] && [ "$selection" -le "$count" ]; then
        local demos=($(grep -o 'func testDemo_[a-zA-Z]*' "$DEMOS_FILE" | sed 's/func //' | sort))
        local test_name=${demos[$((selection-1))]}
        echo ""
        record_demo "$test_name"
    elif [ "$selection" = "q" ]; then
        echo "Goodbye!"
        exit 0
    else
        echo -e "${RED}Invalid selection${NC}"
        exit 1
    fi
}

# Main
case "${1:-}" in
    "")
        interactive_mode
        ;;
    "list"|"-l"|"--list")
        show_header
        list_demos
        ;;
    "all"|"-a"|"--all")
        show_header
        record_all_demos
        ;;
    "help"|"-h"|"--help")
        show_header
        show_usage
        ;;
    [0-9]*)
        # Record by number
        show_header
        local demos=($(grep -o 'func testDemo_[a-zA-Z]*' "$DEMOS_FILE" | sed 's/func //' | sort))
        local count=${#demos[@]}
        if [ "$1" -ge 1 ] && [ "$1" -le "$count" ]; then
            local test_name=${demos[$((1-1))]}
            record_demo "$test_name"
        else
            echo -e "${RED}Invalid demo number. Available: 1-$count${NC}"
            exit 1
        fi
        ;;
    testDemo_*)
        # Record by test name
        show_header
        record_demo "$1"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo ""
        show_usage
        exit 1
        ;;
esac
