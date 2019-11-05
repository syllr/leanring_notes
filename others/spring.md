# 在静态方法中获取bean
ApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
DictionaryRepository dictionaryRepository = context.getBean("dictionaryRepositoryImpl", DictionaryRepository.class);