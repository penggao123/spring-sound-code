/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 *
 *调用bean工厂的后置处理器
 * 1)BeanDefinitionRegistryPostProcessor(先被执行)
 *   所有的bean定义信息将要被加载到容器中，Bean实例还没有被初始化
 * 2)BeanFactoryPostProcessor(后执行)
 *   所有的Bean定义信息已经加载到容器中，但是Bean实例还没有被初始化.
 * 该方法的的作用就是用于ioc容器加载bean定义前后进行处理
 * BeanDefinitionRegistryPostProcessor是bean定义解析前调用
 * 	   1)实现了PriorityOrdered接口的
 * 	   2)实现了Ordered接口的
 * 	   3)没有实现任何的优先级接口的
 * 	   4)因为BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor接口的子接口，实现BeanFactoryPostProcessor的方法
 * BeanFactoryPostProcessor是bean定义解析后调用
 *     1)实现了PriorityOrdered接口的
 * 	   2)实现了Ordered接口的
 * 	   3)没有实现任何的优先级接口的
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		//调用BeanDefinitionRegistryPostProcessor的后置处理器 Begin
		// 定义已处理的后置处理器
		Set<String> processedBeans = new HashSet<>();
		//TODO 当前beanFactory是DefaultListableBeanFactory, DefaultListableBeanFactory是BeanDefinitionRegistry接口的实现类
		//判断我们的beanFactory实现了BeanDefinitionRegistry(实现了该结构就有注册和获取Bean定义的能力）
		if (beanFactory instanceof BeanDefinitionRegistry) {
			//强行把我们的bean工厂转为BeanDefinitionRegistry，因为待会需要注册Bean定义
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			//保存BeanFactoryPostProcessor类型的后置   BeanFactoryPostProcessor 提供修改   TODO 用于保存BeanFactoryPostProcessor
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			//保存BeanDefinitionRegistryPostProcessor类型的后置处理器 BeanDefinitionRegistryPostProcessor 提供注册
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			//循环我们传递进来的beanFactoryPostProcessors
			// TODO 正常情况下，beanFactoryPostProcessors是肯定没有数据的，是我们手动添加的，因为beanFactoryPostProcessors是获取手动添加的，而不是spring扫描的，只有手动annotationConfigApplicationContext.addBeanFactoryPostProcessor(XXX)才会有数据
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				//TODO  判断我们的后置处理器是不是BeanDefinitionRegistryPostProcessor,因为BeanDefinitionRegistryPostProcessor继承了BeanFactoryPostProcessor，所以这里先要判断是不是BeanDefinitionRegistryPostProcessor
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					//进行强制转化
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					//调用他作为BeanDefinitionRegistryPostProcessor的处理器的后置方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					//添加到我们用于保存的BeanDefinitionRegistryPostProcessor的集合中
					registryProcessors.add(registryProcessor);
				}
				else {//若没有实现BeanDefinitionRegistryPostProcessor 接口，那么他就是BeanFactoryPostProcessor
					//把当前的后置处理器加入到regularPostProcessors中
					regularPostProcessors.add(postProcessor);
				}
			}

			//定义一个集合用户保存当前准备创建的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			//第一步:去容器中获取BeanDefinitionRegistryPostProcessor类型的bean的处理器名称
			//TODO  一般情况下，只会找到一个，就是org.springframework.context.annotation.internalConfigurationAnnotationProcessor也就是：ConfigurationAnnotationProcessor
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			//循环筛选出来的匹配BeanDefinitionRegistryPostProcessor的类型名称
			for (String ppName : postProcessorNames) {
				//判断是否实现了PriorityOrdered接口，如果实现了是以最优先进行调用
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					//显示的调用getBean()的方式获取出该对象然后加入到currentRegistryProcessors集合中去
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//同时也加入到processedBeans集合中去
					processedBeans.add(ppName);
				}
			}
			//对currentRegistryProcessors集合中BeanDefinitionRegistryPostProcessor进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 把当前的加入到总的里面去
			//TODO  合并Processors，为什么要合并，因为registryProcessors是装载BeanDefinitionRegistryPostProcessor的一开始的时候，
			// spring只会执行BeanDefinitionRegistryPostProcessor独有的方法
			// 而不会执行BeanDefinitionRegistryPostProcessor父类的方法，即BeanFactoryProcessor的方法
			// 所以这里需要把处理器放入一个集合中，后续统一执行父类的方法
			registryProcessors.addAll(currentRegistryProcessors);
			/**
			 * 在这里典型的BeanDefinitionRegistryPostProcessor就是ConfigurationClassPostProcessor
			 * 用于进行bean定义的加载 比如我们的包扫描，@import  等等。。。。。。。。。
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);//此处调用是为了注册Bean定义
			//调用完之后，马上clea掉
			currentRegistryProcessors.clear();
//---------------------------------------调用内置实现PriorityOrdered接口ConfigurationClassPostProcessor完毕--优先级No1-End----------------------------------------------------------------------------------------------------------------------------
			//去容器中获取BeanDefinitionRegistryPostProcessor的bean的处理器名称（内置的和上面注册的）


			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			//循环上一步获取的BeanDefinitionRegistryPostProcessor的类型名称
			for (String ppName : postProcessorNames) {
				//表示没有被处理过,且实现了Ordered接口的
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					//显示的调用getBean()的方式获取出该对象然后加入到currentRegistryProcessors集合中去
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//同时也加入到processedBeans集合中去
					processedBeans.add(ppName);
				}
			}
			//对currentRegistryProcessors集合中BeanDefinitionRegistryPostProcessor进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//把他加入到用于保存到registryProcessors中
			registryProcessors.addAll(currentRegistryProcessors);
			//调用他的后置处理方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//调用完之后，马上clea掉
			currentRegistryProcessors.clear();
//-----------------------------------------调用自定义Order接口BeanDefinitionRegistryPostProcessor完毕-优先级No2-End-----------------------------------------------------------------------------------------------------------------------------
			//调用没有实现任何优先级接口的BeanDefinitionRegistryPostProcessor
			//定义一个重复处理的开关变量 默认值为true
			boolean reiterate = true;
			//第一次就可以进来
			while (reiterate) {
				//进入循环马上把开关变量给改为false
				reiterate = false;
				//去容器中获取BeanDefinitionRegistryPostProcessor的bean的处理器名称
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				//循环上一步获取的BeanDefinitionRegistryPostProcessor的类型名称
				for (String ppName : postProcessorNames) {
					//没有被处理过的
					if (!processedBeans.contains(ppName)) {
						//显示的调用getBean()的方式获取出该对象然后加入到currentRegistryProcessors集合中去
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						//同时也加入到processedBeans集合中去
						processedBeans.add(ppName);
						//再次设置为true
						reiterate = true;
					}
				}
				//对currentRegistryProcessors集合中BeanDefinitionRegistryPostProcessor进行排序
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				//把他加入到用于保存到registryProcessors中
				registryProcessors.addAll(currentRegistryProcessors);
				//调用他的后置处理方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				//进行clear
				currentRegistryProcessors.clear();
			}
//-----------------------------------------调用没有实现任何优先级接口自定义BeanDefinitionRegistryPostProcessor完毕--End-----------------------------------------------------------------------------------------------------------------------------
			//最后调用BeanDefinitionRegistryPostProcessor、又调用BeanFactoryPostProcessor的后置处理器中的postProcessBeanFactory方法方法
			// 调用 BeanDefinitionRegistryPostProcessor.postProcessBeanFactory方法
			//TODO  上面的代码是执行子类独有的方法，这里需要再把父类的方法也执行一次
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			//调用BeanFactoryPostProcessor 自设的（没有）
			//TODO  regularPostProcessors装载BeanFactoryPostProcessor，执行BeanFactoryPostProcessor的方法
			// 但是regularPostProcessors一般情况下，是不会有数据的，只有在外面手动添加BeanFactoryPostProcessor，才会有数据
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			 //若当前的beanFactory没有实现了BeanDefinitionRegistry 说明没有注册Bean定义的能力
			 // 那么就直接调用BeanDefinitionRegistryPostProcessor.postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

//-----------------------------------------所有BeanDefinitionRegistryPostProcessor调用完毕--End-----------------------------------------------------------------------------------------------------------------------------


//-----------------------------------------处理BeanFactoryPostProcessor --Begin-----------------------------------------------------------------------------------------------------------------------------

		//获取容器中所有的 BeanFactoryPostProcessor
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		//保存BeanFactoryPostProcessor类型实现了priorityOrdered
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//保存BeanFactoryPostProcessor类型实现了Ordered接口的
		List<String> orderedPostProcessorNames = new ArrayList<>();
		//保存BeanFactoryPostProcessor没有实现任何优先级接口的
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			//processedBeans包含的话，表示在上面处理BeanDefinitionRegistryPostProcessor的时候处理过了
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			//判断是否实现了PriorityOrdered 优先级最高，加入到priorityOrderedPostProcessors
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			//判断是否实现了Ordered  优先级 其次，加入到orderedPostProcessorNames
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			//没有实现任何的优先级接口的  最后调用
			//TODO 如果既没有实现PriorityOrdered，也没有实现Ordered。加入到nonOrderedPostProcessorNames
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}
		//  排序处理priorityOrderedPostProcessors，即实现了PriorityOrdered接口的BeanFactoryPostProcessor
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 先调用BeanFactoryPostProcessor实现了 PriorityOrdered接口的
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		//再调用BeanFactoryPostProcessor实现了 Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		//执行既没有实现PriorityOrdered接口，也没有实现Ordered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);
//-----------------------------------------处理BeanFactoryPostProcessor --End-----------------------------------------------------------------------------------------------------------------------------

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();

//------------------------- BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor调用完毕 --End-----------------------------------------------------------------------------------------------------------------------------

	}

	/**
	 * 给我们容器中注册了我们bean的后置处理器
	 * bean的后置处理器在什么时候进行调用？在bean的各个生命周期中都会进行调用
	 * @param beanFactory
	 * @param applicationContext
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		//去容器中获取所有的BeanPostProcessor 的名称(还是bean定义)
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		/**
		 * bean的后置处理器的个数 beanFactory.getBeanPostProcessorCount()成品的个数 之前refresh-->prepareBeanFactory()中注册的
		 * postProcessorNames.length  beanFactory工厂中bean定义的个数
		 * +1 在后面又马上注册了BeanPostProcessorChecker的后置处理器
		 */
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		/**
		 * 按照BeanPostProcessor实现的优先级接口来分离我们的后置处理器
		 */
		//保存实现了priorityOrdered接口的
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//系统内部的
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		//实现了我们ordered接口的
		List<String> orderedPostProcessorNames = new ArrayList<>();
		//没有优先级的
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		//循环我们的bean定义(BeanPostProcessor)
		for (String ppName : postProcessorNames) {
			//若实现了PriorityOrdered接口的
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				//显示的调用getBean流程创建bean的后置处理器
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				//加入到集合中
				priorityOrderedPostProcessors.add(pp);
				//判断是否实现了MergedBeanDefinitionPostProcessor
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					//加入到集合中
					internalPostProcessors.add(pp);
				}
			}
			//判断是否实现了Ordered
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			//没有任何拍下接口的
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// 把实现了priorityOrdered注册到容器中
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// 处理实现Ordered的bean定义
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			//显示调用getBean方法
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			//加入到集合中
			orderedPostProcessors.add(pp);
			//判断是否实现了MergedBeanDefinitionPostProcessor
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				//加入到集合中
				internalPostProcessors.add(pp);
			}
		}
		//排序并且注册我们实现了Order接口的后置处理器
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// 实例化我们所有的非排序接口的
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			//显示调用
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			//判断是否实现了MergedBeanDefinitionPostProcessor
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		//注册我们普通的没有实现任何排序接口的
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		//注册.MergedBeanDefinitionPostProcessor类型的后置处理器 bean 合并后的处理， Autowired 注解正是通过此方法实现诸如类型的预解析。
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		//注册ApplicationListenerDetector 应用监听器探测器的后置处理器
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {
		//获取容器中的ConfigurationClassPostProcessor的后置处理器进行bean定义的扫描
		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
