/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl.Topology;

import com.google.common.base.Preconditions;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.DirectedSparseMultigraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of NetworkGraphService
 * It uses Jung graph library internally to maintain a graph and optimum way to
 * return shortest path using Dijkstra algorithm.
 */
public class NetworkGraphImpl implements NetworkGraphService {

	private static final Logger _logger = LoggerFactory
			.getLogger(NetworkGraphImpl.class);

	private boolean graphUpdated = false;

	Graph<NodeId, Link> networkGraph = null;
	Graph<NodeId, Link> directedNetworkGraph = null;
	Set<String> undirectedLinkAdded = new HashSet<>();
	Set<String> directedLinkAdded = new HashSet<>();

	// Enable following lines when shortest path functionality is required.
	DijkstraShortestPath<NodeId, Link> shortestPath = null;

	/**
	 * Adds links to existing graph or creates new directed graph with given
	 * links if graph was not initialized.
	 *
	 * @param links
	 *            The links to add.
	 */
	@Override
	public synchronized void addLinks(List<Link> links) {
		if (links == null || links.isEmpty()) {
			_logger.info("In addLinks: No link added as links is null or empty.");
			return;
		}

		_logger.debug("Adding links");

		if (networkGraph == null) {
			networkGraph = new SparseMultigraph<>();
			directedNetworkGraph = new DirectedSparseMultigraph<>();
		}

		for (Link link : links) {
			if(!directedLinkAlreadyAdded(link)) {
				addDirectedLink(link);
			}
			if (!undirectedLinkAlreadyAdded(link)) {
				addUndirectedLink(link);
			}
		}

		if (shortestPath == null) {
			shortestPath = new DijkstraShortestPath<>(networkGraph);
		} else {
			shortestPath.reset();
		}

		graphUpdated = true;
	}

	private boolean undirectedLinkAlreadyAdded(Link link) {
		String linkAddedKey = null;
		if (link.getDestination().getDestTp().hashCode() > link.getSource()
				.getSourceTp().hashCode()) {
			linkAddedKey = link.getSource().getSourceTp().getValue()
					+ link.getDestination().getDestTp().getValue();
		} else {
			linkAddedKey = link.getDestination().getDestTp().getValue()
					+ link.getSource().getSourceTp().getValue();
		}
		if (undirectedLinkAdded.contains(linkAddedKey)) {
			return true;
		} else {
			undirectedLinkAdded.add(linkAddedKey);
			return false;
		}
	}

	private boolean directedLinkAlreadyAdded(Link link) {
		String linkAddedKey = null;
		linkAddedKey = link.getLinkId().getValue();
		if (directedLinkAdded.contains(linkAddedKey)) {
			return true;
		} else {
			directedLinkAdded.add(linkAddedKey);
			return false;
		}
	}

	private void addUndirectedLink(Link link) {
		NodeId sourceNodeId = link.getSource().getSourceNode();
		NodeId destinationNodeId = link.getDestination().getDestNode();
		_logger.debug("Adding link between " + sourceNodeId.getValue()
				+ " and " + destinationNodeId.getValue());
		networkGraph.addVertex(sourceNodeId);
		networkGraph.addVertex(destinationNodeId);
		networkGraph.addEdge(link, sourceNodeId, destinationNodeId,
				EdgeType.UNDIRECTED);
	}

	private void addDirectedLink(Link link) {
		NodeId sourceNodeId = link.getSource().getSourceNode();
		NodeId destinationNodeId = link.getDestination().getDestNode();
		_logger.debug("Adding link between " + sourceNodeId.getValue()
				+ " and " + destinationNodeId.getValue());
		directedNetworkGraph.addVertex(sourceNodeId);
		directedNetworkGraph.addVertex(destinationNodeId);
		directedNetworkGraph.addEdge(link, sourceNodeId, destinationNodeId);
	}

	/**
	 * Removes links from existing graph.
	 *
	 * @param links
	 *            The links to remove.
	 */
	@Override
	public synchronized void removeLinks(List<Link> links) {
		Preconditions.checkNotNull(networkGraph,
				"Graph is not initialized, add links first.");

		if (links == null || links.isEmpty()) {
			_logger.info("In removeLinks: No link removed as links is null or empty.");
			return;
		}

		_logger.debug("Removing links");

		for (Link link : links) {
			networkGraph.removeEdge(link);
			if(directedNetworkGraph.containsEdge(link)) {
				directedNetworkGraph.removeEdge(link);
			}
		}
		if (shortestPath == null) {
			shortestPath = new DijkstraShortestPath<>(networkGraph);
		} else {
			shortestPath.reset();
		}

		graphUpdated = true;
	}

	/**
	 * returns a path between 2 nodes. Uses Dijkstra's algorithm to return
	 * shortest path.
	 *
	 * @param sourceNodeId
	 * @param destinationNodeId
	 * @return
	 */
	public synchronized List<Link> getPath(NodeId sourceNodeId,
			NodeId destinationNodeId) {
		Preconditions.checkNotNull(shortestPath,
				"Graph is not initialized, add links first.");

		if (sourceNodeId == null || destinationNodeId == null) {
			_logger.info("In getPath: returning null, as sourceNodeId or destinationNodeId is null.");
			return null;
		}

		if (!hasNode(sourceNodeId) || !hasNode(destinationNodeId)) {
			_logger.info("In getPath: returning null, " + sourceNodeId + " or "
					+ destinationNodeId + " not in graph");
			return null;
		}

		return shortestPath.getPath(sourceNodeId, destinationNodeId);
	}

	/**
	 * Clears the prebuilt graph, in case same service instance is required to
	 * process a new graph.
	 */
	@Override
	public synchronized void clear() {
		networkGraph = null;
		directedNetworkGraph = null;
		shortestPath = null;
		undirectedLinkAdded.clear();
		directedLinkAdded.clear();
	}

	/**
	 * Get all the links in the network.
	 *
	 * @return The links in the network.
	 */
	@Override
	public List<Link> getAllLinks() {
		List<Link> allLinks = new ArrayList<>();
		if (networkGraph != null) {
			allLinks.addAll(networkGraph.getEdges());
		}
		return allLinks;
	}

	@Override
	public synchronized boolean hasNode(NodeId nodeId) {
		if (networkGraph != null) {
			return networkGraph.containsVertex(nodeId);
		}
		return false;
	}

	@Override
	public synchronized Graph<NodeId, Link> getGraphCopy() {
		if(networkGraph == null) {
			return null;
		}

		Graph<NodeId, Link> output = new SparseMultigraph<>();

		for (NodeId v : networkGraph.getVertices()) {
			output.addVertex(v);
		}

		for (Link e : networkGraph.getEdges()) {
			output.addEdge(e, networkGraph.getIncidentVertices(e));
		}

		return output;
	}

	@Override
	public synchronized void printGraph() {
		_logger.info("Printing network graph");
		if (networkGraph == null) {
			_logger.warn("Graph is null");
		} else {
			_logger.info("Printing vertices");
			ArrayList<NodeId> vertices = new ArrayList<>();
			vertices.addAll(networkGraph.getVertices());

			for (NodeId v : vertices) {
				ArrayList<Link> links = new ArrayList<>();
				links.addAll(networkGraph.getIncidentEdges(v));
				_logger.info("Node " + v.getValue() + " links:");
				if (links != null && !links.isEmpty()) {
					for (Link l : links) {
						_logger.info(l.getSource().getSourceTp().getValue()
								+ " - "
								+ l.getDestination().getDestTp().getValue()
								+ " (" + l.getLinkId().getValue() + ")");
					}
				}
			}
		}
	}

	public boolean isGraphUpdated() {
		return graphUpdated;
	}

	public synchronized void setGraphUpdated(boolean graphUpdated) {
		this.graphUpdated = graphUpdated;
	}

	@Override
	public Graph<NodeId, Link> getNetworkGraph() {
		return networkGraph;
	}

	@Override
	public Graph<NodeId, Link> getDirectedNetworkGraph() {
		return directedNetworkGraph;
	}

}
