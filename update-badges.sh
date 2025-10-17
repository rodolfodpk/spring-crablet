#!/bin/bash

# Script to update test coverage badges based on current JaCoCo report
# Usage: ./update-badges.sh

set -e

echo "Updating test coverage badges..."

# Check if JaCoCo report exists
if [ ! -f "target/site/jacoco/jacoco.xml" ]; then
    echo "Error: JaCoCo report not found. Run 'mvn test verify' first."
    exit 1
fi

# Extract coverage percentages using Python
python3 -c "
import xml.etree.ElementTree as ET
import subprocess
import sys

# Parse the JaCoCo report
tree = ET.parse('target/site/jacoco/jacoco.xml')
root = tree.getroot()

# Get overall line coverage
counters = root.findall('.//counter[@type=\"LINE\"]')
last_counter = counters[-1]
missed = int(last_counter.get('missed', 0))
covered = int(last_counter.get('covered', 0))
total = missed + covered
line_coverage = (covered / total) * 100

# Get overall branch coverage
branch_counters = root.findall('.//counter[@type=\"BRANCH\"]')
last_branch_counter = branch_counters[-1]
branch_missed = int(last_branch_counter.get('missed', 0))
branch_covered = int(last_branch_counter.get('covered', 0))
branch_total = branch_missed + branch_covered
branch_coverage = (branch_covered / branch_total) * 100

print(f'{line_coverage:.1f}')
print(f'{branch_coverage:.1f}')
" > /tmp/coverage_values.txt

# Read the coverage values
LINE_COVERAGE=$(sed -n '1p' /tmp/coverage_values.txt)
BRANCH_COVERAGE=$(sed -n '2p' /tmp/coverage_values.txt)

echo "Current coverage:"
echo "  Line Coverage: ${LINE_COVERAGE}%"
echo "  Branch Coverage: ${BRANCH_COVERAGE}%"

# Determine badge colors
if (( $(echo "$LINE_COVERAGE >= 80" | bc -l) )); then
    LINE_COLOR="4c1"
elif (( $(echo "$LINE_COVERAGE >= 60" | bc -l) )); then
    LINE_COLOR="yellow"
else
    LINE_COLOR="red"
fi

if (( $(echo "$BRANCH_COVERAGE >= 80" | bc -l) )); then
    BRANCH_COLOR="4c1"
elif (( $(echo "$BRANCH_COVERAGE >= 60" | bc -l) )); then
    BRANCH_COLOR="yellow"
else
    BRANCH_COLOR="red"
fi

# Download updated badges
echo "Downloading updated badges..."

curl -s -o .github/badges/jacoco.svg "https://img.shields.io/badge/coverage-${LINE_COVERAGE}%25-${LINE_COLOR}.svg"
curl -s -o .github/badges/branches.svg "https://img.shields.io/badge/branches-${BRANCH_COVERAGE}%25-${BRANCH_COLOR}.svg"

echo "✅ Badges updated successfully!"
echo "   Coverage: ${LINE_COVERAGE}%"
echo "   Branches: ${BRANCH_COVERAGE}%"

# Clean up
rm -f /tmp/coverage_values.txt
