/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.ng.request.mapper;

import org.apache.wicket.ng.request.Request;
import org.apache.wicket.ng.request.RequestHandler;
import org.apache.wicket.ng.request.RequestMapper;
import org.apache.wicket.ng.request.Url;
import org.apache.wicket.ng.request.component.PageParameters;
import org.apache.wicket.ng.request.component.RequestablePage;
import org.apache.wicket.ng.request.handler.PageAndComponentProvider;
import org.apache.wicket.ng.request.handler.PageProvider;
import org.apache.wicket.ng.request.handler.impl.BookmarkableListenerInterfaceRequestHandler;
import org.apache.wicket.ng.request.handler.impl.BookmarkablePageRequestHandler;
import org.apache.wicket.ng.request.handler.impl.ListenerInterfaceRequestHandler;
import org.apache.wicket.ng.request.handler.impl.RenderPageRequestHandler;
import org.apache.wicket.ng.request.listener.RequestListenerInterface;
import org.apache.wicket.ng.request.mapper.info.ComponentInfo;
import org.apache.wicket.ng.request.mapper.info.PageComponentInfo;
import org.apache.wicket.ng.request.mapper.info.PageInfo;
import org.apache.wicket.ng.util.lang.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract encoder for Bookmarkable, Hybrid and BookmarkableListenerInterface URLs.
 * 
 * @author Matej Knopp
 */
public abstract class AbstractBookmarkableMapper extends AbstractMapper
{
	/**
	 * Construct.
	 */
	public AbstractBookmarkableMapper()
	{
	}

	/**
	 * Represents information stored in URL.
	 * 
	 * @author Matej Knopp
	 */
	protected static final class UrlInfo
	{
		private final PageComponentInfo pageComponentInfo;
		private final PageParameters pageParameters;
		private final Class<? extends RequestablePage> pageClass;

		/**
		 * Construct.
		 * 
		 * @param pageComponentInfo
		 *            optional parameter providing the page instance and component information
		 * @param pageClass
		 *            mandatory parameter
		 * @param pageParameters
		 *            optional parameter providing pageParameters
		 */
		public UrlInfo(PageComponentInfo pageComponentInfo, Class<? extends RequestablePage> pageClass,
				PageParameters pageParameters)
		{
			Check.argumentNotNull(pageClass, "pageClass");
			
			this.pageComponentInfo = pageComponentInfo;
			this.pageParameters = pageParameters != null ? pageParameters : null;
			this.pageClass = pageClass;
		}

		/**
		 * @return PageComponentInfo instance or <code>null</code>
		 */
		public PageComponentInfo getPageComponentInfo()
		{
			return pageComponentInfo;
		}

		/**
		 * @return page class
		 */
		public Class<? extends RequestablePage> getPageClass()
		{
			return pageClass;
		}

		/**
		 * @return PageParameters instance (never <code>null</code>)
		 */
		public PageParameters getPageParameters()
		{
			return pageParameters;
		}
	}

	/**
	 * Parse the given request to an {@link UrlInfo} instance.
	 * 
	 * @param request
	 * @return UrlInfo instance or <code>null</code> if this encoder can not handle the request
	 */
	protected abstract UrlInfo parseRequest(Request request);

	/**
	 * Builds URL for the given {@link UrlInfo} instance. The URL this method produces must be
	 * parseable by the {@link #parseRequest(Request)} method.
	 * 
	 * @param info
	 * @return Url result URL
	 */
	protected abstract Url buildUrl(UrlInfo info);

	/**
	 * Indicates whether hybrid {@link RenderPageRequestHandler} URL for page will be generated only
	 * if page has been created with bookmarkable URL.
	 * <p>
	 * Generic bookmarkable encoders this method should return <code>true</code>. For explicit
	 * (mounted) encoders this method should return <code>false</code>
	 * 
	 * @return <code>true</code> if hybrid URL requires page created bookmarkable,
	 *         <code>false</code> otherwise.
	 */
	protected abstract boolean pageMustHaveBeenCreatedBookmarkable();
	
	/**
	 * @see RequestMapper#getCompatibilityScore(Request)
	 */
	public abstract int getCompatibilityScore(Request request);
	
	private RequestHandler processBookmarkable(Class<? extends RequestablePage> pageClass, PageParameters pageParameters)
	{
		PageProvider provider = new PageProvider(pageClass, pageParameters);
		provider.setPageSource(getContext());
		return new RenderPageRequestHandler(provider);
	}

	private RequestHandler processHybrid(PageInfo pageInfo, Class<? extends RequestablePage> pageClass,
			PageParameters pageParameters, Integer renderCount)
	{
		PageProvider provider = new PageProvider(pageInfo.getPageId(), pageClass, pageParameters,
				renderCount);
		provider.setPageSource(getContext());
		return new RenderPageRequestHandler(provider);
	}

	private RequestHandler processListener(PageComponentInfo pageComponentInfo, Class<? extends RequestablePage> pageClass,
			PageParameters pageParameters)
	{
		PageInfo pageInfo = pageComponentInfo.getPageInfo();
		ComponentInfo componentInfo = pageComponentInfo.getComponentInfo();
		Integer renderCount = componentInfo != null ? componentInfo.getRenderCount() : null;

		RequestListenerInterface listenerInterface = requestListenerInterfaceFromString(componentInfo
				.getListenerInterface());

		if (listenerInterface != null)
		{
			PageAndComponentProvider provider = new PageAndComponentProvider(pageInfo.getPageId(), pageClass,
					pageParameters, renderCount, componentInfo.getComponentPath());

			provider.setPageSource(getContext());
			
			return new ListenerInterfaceRequestHandler(provider, listenerInterface, componentInfo.getBehaviorIndex());	
		}
		else
		{
			logger.warn("Unknown listener interface '" + componentInfo.getListenerInterface() + "'");
			return null;
		}		
	}
	
	public RequestHandler mapRequest(Request request)
	{
		UrlInfo urlInfo = parseRequest(request);

		// check if the URL is long enough and starts with the proper segments
		if (urlInfo != null)
		{
			PageComponentInfo info = urlInfo.getPageComponentInfo();
			Class<? extends RequestablePage> pageClass = urlInfo.getPageClass();
			PageParameters pageParameters = urlInfo.getPageParameters();

			if (info == null || info.getPageInfo().getPageId() == null)
			{
				// if there are is no page instance information (only page map name - optionally)
				// then this is a simple bookmarkable URL
				return processBookmarkable(pageClass, pageParameters);
			}
			else if (info.getPageInfo().getPageId() != null && info.getComponentInfo() == null)
			{
				// if there is page instance information in the URL but no component and listener
				// interface then this is a hybrid URL - we need to try to reuse existing page
				// instance
				return processHybrid(info.getPageInfo(), pageClass, pageParameters, null);
			}
			else if (info.getComponentInfo() != null)
			{
				// with both page instance and component+listener this is a listener interface URL
				return processListener(info, pageClass, pageParameters);
			}
		}
		return null;
	}
	
	protected boolean checkPageClass(Class<? extends RequestablePage> pageClass)
	{
		return true;
	}

	public Url mapHandler(RequestHandler requestHandler)
	{
		if (requestHandler instanceof BookmarkablePageRequestHandler)
		{
			// simple bookmarkable URL with no page instance information
			BookmarkablePageRequestHandler handler = (BookmarkablePageRequestHandler) requestHandler;

			if (!checkPageClass(handler.getPageClass()))
			{
				return null;
			}

			PageInfo info = new PageInfo();
			UrlInfo urlInfo = new UrlInfo(new PageComponentInfo(info, null), handler.getPageClass(), handler
					.getPageParameters());

			return buildUrl(urlInfo);
		}
		else if (requestHandler instanceof RenderPageRequestHandler)
		{
			// possibly hybrid URL - bookmarkable URL with page instance information
			// but only allowed if the page was created by bookamarkable URL

			RenderPageRequestHandler handler = (RenderPageRequestHandler) requestHandler;
			RequestablePage page = handler.getPage();

			if (!checkPageClass(page.getClass()))
			{
				return null;
			}

			if (!pageMustHaveBeenCreatedBookmarkable() || page.wasCreatedBookmarkable())
			{
				PageInfo info = null;
				if (!page.isPageStateless())
				{
					info = new PageInfo(page);
				}
				PageComponentInfo pageComponentInfo = info != null ? new PageComponentInfo(info, null) : null;

				UrlInfo urlInfo = new UrlInfo(pageComponentInfo, page.getClass(), handler.getPageParameters());
				return buildUrl(urlInfo);
			}
			else
			{
				return null;
			}

		}
		else if (requestHandler instanceof BookmarkableListenerInterfaceRequestHandler)
		{
			// listener interface URL with page class information
			BookmarkableListenerInterfaceRequestHandler handler = (BookmarkableListenerInterfaceRequestHandler) requestHandler;
			RequestablePage page = handler.getPage();

			if (!checkPageClass(page.getClass()))
			{
				return null;
			}

			PageInfo pageInfo = new PageInfo(page);
			ComponentInfo componentInfo = new ComponentInfo(page.getRenderCount(),
					requestListenerInterfaceToString(handler.getListenerInterface()), handler.getComponent().getPath(),
					handler.getBehaviorIndex());

			UrlInfo urlInfo = new UrlInfo(new PageComponentInfo(pageInfo, componentInfo), page.getClass(), handler
					.getPageParameters());
			return buildUrl(urlInfo);
		}

		return null;
	}	

	
	private static Logger logger = LoggerFactory.getLogger(AbstractBookmarkableMapper.class);
}