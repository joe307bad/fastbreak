import React, { useEffect, useRef, useState } from 'https://esm.sh/react@18';
import ReactDOM from 'https://esm.sh/react-dom@18/client';
import * as d3 from 'https://esm.sh/d3@7';

const LineChart = () => {
    const svgRef = useRef(null);
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [overallStats, setOverallStats] = useState(null);

    useEffect(() => {
        // Load data from output.json
        fetch('./output.json')
            .then(response => response.json())
            .then(jsonData => {
                setOverallStats(jsonData.overallStats);

                // Transform the data for D3 charts
                const transformedData = [
                    {
                        name: 'Top Ten Sleeper Hits',
                        color: '#4a90e2',
                        values: jsonData.weeklyPredictions.map(d => ({
                            x: d.week,
                            y: d.sleeperTop10Hits
                        }))
                    },
                    {
                        name: 'Top Three Sleeper Hits',
                        color: '#ff6b6b',
                        values: jsonData.weeklyPredictions.map(d => ({
                            x: d.week,
                            y: d.sleeperTop3Hits
                        }))
                    },
                    {
                        name: 'ML Model Top 10 Hits',
                        color: '#51cf66',
                        values: jsonData.weeklyPredictions.map(d => ({
                            x: d.week,
                            y: d.mlTop10Hits
                        }))
                    },
                    {
                        name: 'ML Model Top 3 Hits',
                        color: '#ffa726',
                        values: jsonData.weeklyPredictions.map(d => ({
                            x: d.week,
                            y: d.mlTop3Hits
                        }))
                    }
                ];

                setData(transformedData);
                setLoading(false);
            })
            .catch(error => {
                console.error('Error loading data:', error);
                setLoading(false);
            });
    }, []);

    useEffect(() => {
        if (!svgRef.current || !data.length || loading) return;

        const margin = { top: 40, right: 150, bottom: 60, left: 60 };
        const width = 1000 - margin.left - margin.right;
        const height = 500 - margin.top - margin.bottom;

        d3.select(svgRef.current).selectAll("*").remove();

        const svg = d3.select(svgRef.current)
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom);

        const g = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        const allValues = data.flatMap(d => d.values);
        const weeks = [...new Set(allValues.map(d => d.x))].sort((a, b) => a - b);

        const xScale = d3.scaleLinear()
            .domain(d3.extent(weeks))
            .range([0, width]);

        const yScale = d3.scaleLinear()
            .domain([0, d3.max(allValues, d => d.y) + 1])
            .range([height, 0]);

        const sizeScale = d3.scaleLinear()
            .domain([0, d3.max(allValues, d => d.y)])
            .range([3, 8]);

        const line = d3.line()
            .x(d => xScale(d.x))
            .y(d => yScale(d.y))
            .curve(d3.curveMonotoneX);

        // Add gridlines
        g.append("g")
            .attr("class", "grid")
            .attr("transform", `translate(0,${height})`)
            .call(d3.axisBottom(xScale)
                .tickSize(-height)
                .tickFormat("")
            )
            .style("stroke-dasharray", "3,3")
            .style("opacity", 0.3);

        g.append("g")
            .attr("class", "grid")
            .call(d3.axisLeft(yScale)
                .tickSize(-width)
                .tickFormat("")
            )
            .style("stroke-dasharray", "3,3")
            .style("opacity", 0.3);

        // Add axes
        g.append("g")
            .attr("class", "axis")
            .attr("transform", `translate(0,${height})`)
            .call(d3.axisBottom(xScale).tickFormat(d3.format("d")));

        g.append("text")
            .attr("class", "axis-label")
            .attr("x", width / 2)
            .attr("y", height + 40)
            .style("text-anchor", "middle")
            .text("Week");

        g.append("g")
            .attr("class", "axis")
            .call(d3.axisLeft(yScale));

        g.append("text")
            .attr("class", "axis-label")
            .attr("transform", "rotate(-90)")
            .attr("y", -40)
            .attr("x", -height / 2)
            .style("text-anchor", "middle")
            .text("Number of Hits");

        // Remove existing tooltip
        d3.select("body").selectAll(".tooltip").remove();

        const tooltip = d3.select("body").append("div")
            .attr("class", "tooltip")
            .style("opacity", 0)
            .style("position", "absolute")
            .style("background", "rgba(0, 0, 0, 0.8)")
            .style("color", "white")
            .style("border-radius", "4px")
            .style("padding", "8px")
            .style("font-size", "12px")
            .style("pointer-events", "none")
            .style("z-index", "1000");

        // Draw lines and dots
        data.forEach((lineData, i) => {
            // Draw line
            g.append("path")
                .datum(lineData.values)
                .attr("class", "line")
                .attr("d", line)
                .attr("stroke", lineData.color)
                .attr("fill", "none")
                .attr("stroke-width", 2);

            // Draw dots
            g.selectAll(`.dot-${i}`)
                .data(lineData.values)
                .enter().append("circle")
                .attr("class", `dot-${i}`)
                .attr("cx", d => xScale(d.x))
                .attr("cy", d => yScale(d.y))
                .attr("r", d => sizeScale(d.y))
                .attr("fill", lineData.color)
                .attr("stroke", "white")
                .attr("stroke-width", 1)
                .style("cursor", "pointer")
                .on("mouseover", function(event, d) {
                    d3.select(this).attr("r", d => sizeScale(d.y) + 2);
                    tooltip.transition()
                        .duration(200)
                        .style("opacity", .9);
                    tooltip.html(`${lineData.name}<br/>Week: ${d.x}<br/>Hits: ${d.y}`)
                        .style("left", (event.pageX + 10) + "px")
                        .style("top", (event.pageY - 28) + "px");
                })
                .on("mouseout", function(event, d) {
                    d3.select(this).attr("r", d => sizeScale(d.y));
                    tooltip.transition()
                        .duration(500)
                        .style("opacity", 0);
                });
        });

        // Add legend
        const legend = g.append("g")
            .attr("class", "legend")
            .attr("transform", `translate(${width + 20}, 20)`);

        data.forEach((lineData, i) => {
            const legendRow = legend.append("g")
                .attr("transform", `translate(0, ${i * 25})`);

            legendRow.append("rect")
                .attr("width", 12)
                .attr("height", 12)
                .attr("fill", lineData.color);

            legendRow.append("text")
                .attr("x", 18)
                .attr("y", 9)
                .attr("text-anchor", "start")
                .style("font-size", "12px")
                .text(lineData.name);
        });

    }, [data, loading]);

    if (loading) {
        return React.createElement('div', null,
            React.createElement('h2', { className: 'chart-title' }, 'Loading...'));
    }

    return React.createElement('div', null,
        React.createElement('div', { className: 'chart-title' },
            React.createElement('h1', null, 'NFL Fantasy Breakout Prediction Analysis'),
            overallStats && React.createElement('div', { style: { fontSize: '14px', marginTop: '10px', color: '#666' } },
                React.createElement('p', null, `Total Weeks Analyzed: ${overallStats.totalWeeks} | Total Players: ${overallStats.totalAnalyzedPlayers}`),
                React.createElement('p', null, `ML Model Accuracy (Top 10): ${(overallStats.mlAccuracyTop10 * 100).toFixed(1)}% | Sleeper Score Accuracy (Top 10): ${(overallStats.sleeperAccuracyTop10 * 100).toFixed(1)}%`)
            )
        ),
        React.createElement('svg', { ref: svgRef })
    );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(React.createElement(LineChart));