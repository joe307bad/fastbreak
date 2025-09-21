import React, { useEffect, useRef, useState } from 'https://esm.sh/react@18';
import ReactDOM from 'https://esm.sh/react-dom@18/client';
import * as d3 from 'https://esm.sh/d3@7';

const LineChart = () => {
    const svgRef = useRef(null);

    const weeklyData = [
        { Week: 3, TopTenSleeperHits: 6, TopThreeSleeperHits: 3, MLModelSuccessfulHits: 7 },
        { Week: 4, TopTenSleeperHits: 5, TopThreeSleeperHits: 1, MLModelSuccessfulHits: 6 },
        { Week: 5, TopTenSleeperHits: 5, TopThreeSleeperHits: 1, MLModelSuccessfulHits: 6 },
        { Week: 6, TopTenSleeperHits: 2, TopThreeSleeperHits: 1, MLModelSuccessfulHits: 2 },
        { Week: 7, TopTenSleeperHits: 3, TopThreeSleeperHits: 0, MLModelSuccessfulHits: 4 },
        { Week: 8, TopTenSleeperHits: 4, TopThreeSleeperHits: 2, MLModelSuccessfulHits: 7 },
        { Week: 9, TopTenSleeperHits: 5, TopThreeSleeperHits: 2, MLModelSuccessfulHits: 5 },
        { Week: 10, TopTenSleeperHits: 2, TopThreeSleeperHits: 0, MLModelSuccessfulHits: 2 },
        { Week: 11, TopTenSleeperHits: 1, TopThreeSleeperHits: 0, MLModelSuccessfulHits: 2 },
        { Week: 12, TopTenSleeperHits: 0, TopThreeSleeperHits: 0, MLModelSuccessfulHits: 1 },
        { Week: 13, TopTenSleeperHits: 3, TopThreeSleeperHits: 1, MLModelSuccessfulHits: 4 },
        { Week: 14, TopTenSleeperHits: 2, TopThreeSleeperHits: 0, MLModelSuccessfulHits: 4 },
        { Week: 15, TopTenSleeperHits: 3, TopThreeSleeperHits: 2, MLModelSuccessfulHits: 3 },
        { Week: 16, TopTenSleeperHits: 4, TopThreeSleeperHits: 2, MLModelSuccessfulHits: 5 },
        { Week: 17, TopTenSleeperHits: 4, TopThreeSleeperHits: 2, MLModelSuccessfulHits: 6 }
    ]

    const [data] = useState([
        {
            name: 'Top Ten Sleeper Hits',
            color: '#4a90e2',
            values: weeklyData.map(d => ({
                x: d.Week,
                y: d.TopTenSleeperHits
            }))
        },
        {
            name: 'Top Three Sleeper Hits',
            color: '#ff6b6b',
            values: weeklyData.map(d => ({
                x: d.Week,
                y: d.TopThreeSleeperHits
            }))
        },
        {
            name: 'ML Model Successful Hits',
            color: '#51cf66',
            values: weeklyData.map(d => ({
                x: d.Week,
                y: d.MLModelSuccessfulHits
            }))
        }
    ]);

    useEffect(() => {
        if (!svgRef.current || !data.length) return;

        const margin = { top: 40, right: 30, bottom: 60, left: 60 };
        const width = 800 - margin.left - margin.right;
        const height = 400 - margin.top - margin.bottom;

        d3.select(svgRef.current).selectAll("*").remove();

        const svg = d3.select(svgRef.current)
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom);

        const g = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        const allValues = data.flatMap(d => d.values);

        const xScale = d3.scaleLinear()
            .domain([3, 17])
            .range([0, width]);

        const yScale = d3.scaleLinear()
            .domain([0, 10])
            .range([height, 0]);

        const sizeScale = d3.scaleLinear()
            .domain([d3.min(allValues, d => d.y), d3.max(allValues, d => d.y)])
            .range([3, 8]);

        const line = d3.line()
            .x(d => xScale(d.x))
            .y(d => yScale(d.y))
            .curve(d3.curveMonotoneX);

        g.append("g")
            .attr("class", "axis")
            .attr("transform", `translate(0,${height})`)
            .call(d3.axisBottom(xScale));

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

        d3.select("body").selectAll(".tooltip").remove();

        const tooltip = d3.select("body").append("div")
            .attr("class", "tooltip")
            .style("opacity", 0)
            .style("position", "absolute")
            .style("background", "rgba(0, 0, 0, 0.8)")
            .style("color", "white")
            .style("border-radius", "4px")
            .style("padding", "5px")
            .style("font-size", "12px")
            .style("pointer-events", "none");

        data.forEach((lineData, i) => {
            g.append("path")
                .datum(lineData.values)
                .attr("class", "line")
                .attr("d", line)
                .attr("stroke", lineData.color)
                .attr("fill", "none")
                .attr("stroke-width", 2);

            g.selectAll(`.dot-${i}`)
                .data(lineData.values)
                .enter().append("circle")
                .attr("class", `dot-${i}`)
                .attr("cx", d => xScale(d.x))
                .attr("cy", d => yScale(d.y))
                .attr("r", d => sizeScale(d.y))
                .attr("fill", lineData.color)
                .on("mouseover", function(event, d) {
                    tooltip.transition()
                        .duration(200)
                        .style("opacity", .9);
                    tooltip.html(`${lineData.name}<br/>Week: ${d.x}<br/>Hits: ${d.y}`)
                        .style("left", (event.pageX + 10) + "px")
                        .style("top", (event.pageY - 28) + "px");
                })
                .on("mouseout", function() {
                    tooltip.transition()
                        .duration(500)
                        .style("opacity", 0);
                });
        });

        const legend = g.append("g")
            .attr("class", "legend")
            .attr("transform", `translate(${width - 100}, 20)`);

        data.forEach((lineData, i) => {
            const legendRow = legend.append("g")
                .attr("transform", `translate(0, ${i * 20})`);

            legendRow.append("rect")
                .attr("width", 10)
                .attr("height", 10)
                .attr("fill", lineData.color);

            legendRow.append("text")
                .attr("x", 15)
                .attr("y", 9)
                .attr("text-anchor", "start")
                .style("font-size", "12px")
                .text(lineData.name);
        });

    }, [data]);

    return React.createElement('div', null,
        React.createElement('h2', { className: 'chart-title' }, 'Weekly Fantasy Metrics Performance'),
        React.createElement('svg', { ref: svgRef })
    );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(React.createElement(LineChart));